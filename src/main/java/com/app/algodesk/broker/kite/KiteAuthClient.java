package com.app.algodesk.broker.kite;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.broker.BrokerAuthClient;
import com.app.algodesk.broker.BrokerSession;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class KiteAuthClient implements BrokerAuthClient {

    // Kite access tokens are invalidated daily at 6 AM IST
    private static final ZoneId KITE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime KITE_TOKEN_RESET = LocalTime.of(6, 0);

    private final KiteProperties props;

    public KiteAuthClient(KiteProperties props) {
        this.props = props;
    }

    @Override
    public String getLoginUrl(String state) {
        // Kite drops unknown query params; only redirect_params is echoed back to the callback
        String redirectParams = URLEncoder.encode("state=" + state, StandardCharsets.UTF_8);
        return String.format(UrlConstants.KITE_LOGIN_URL, props.getApiKey(), redirectParams);
    }

    @Override
    public String getApiKey() {
        return props.getApiKey();
    }

    @Override
    public void invalidateSession(String accessToken) throws Exception {
        String query = "?api_key=" + encode(props.getApiKey()) + "&access_token=" + encode(accessToken);
        java.net.URL url = new java.net.URL(UrlConstants.BASE_URL + UrlConstants.KITE_SESSION_TOKEN_PATH + query);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("X-Kite-Version", "3");

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException("Session invalidation failed: " + status);
        }
    }

    @Override
    public BrokerSession createSession(String requestToken) throws Exception {
        // Step 1: Create checksum and exchange token with Kite API
        String data = props.getApiKey() + requestToken + props.getApiSecret();
        String checksum = sha256Hex(data);

        java.net.URL url = new java.net.URL(UrlConstants.KITE_TOKEN_EXCHANGE_URL);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("X-Kite-Version", "3");
        conn.setDoOutput(true);

        String body = "api_key=" + encode(props.getApiKey()) + "&request_token=" + encode(requestToken) + "&checksum=" + encode(checksum);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        if (status >= 200 && status < 300) {
            return parseSession(response);
        } else {
            throw new RuntimeException("Token exchange failed: " + status + " " + response);
        }
    }

    // The session/token response already carries the user profile, so no extra /user/profile call is needed
    private BrokerSession parseSession(String json) {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            data = root;
        }
        String accessToken = data.optString("access_token", null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("access_token not found in response");
        }
        return new BrokerSession(
                accessToken,
                data.optString("public_token", null),
                data.optString("refresh_token", null),
                data.optString("user_id", null),
                data.optString("user_name", "Unknown"),
                blankToNull(data.optString("email", null)),
                data.optString("broker", "ZERODHA"),
                nextTokenExpiry()
        );
    }

    private static LocalDateTime nextTokenExpiry() {
        ZonedDateTime now = ZonedDateTime.now(KITE_ZONE);
        ZonedDateTime expiry = now.with(KITE_TOKEN_RESET);
        if (!expiry.isAfter(now)) {
            expiry = expiry.plusDays(1);
        }
        return expiry.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String data) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
