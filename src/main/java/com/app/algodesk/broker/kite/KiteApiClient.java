package com.app.algodesk.broker.kite;

import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static com.app.algodesk.appsetup.UrlConstants.BASE_URL;

@Component
public class KiteApiClient {

    private static final String KITE_VERSION = "3";

    private final KiteProperties props;

    public KiteApiClient(KiteProperties props) {
        this.props = props;
    }

    public String get(String path, String accessToken) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Kite-Version", KITE_VERSION);
        conn.setRequestProperty(
                "Authorization",
                "token " + props.getApiKey() + ":" + accessToken
        );

        String response = connectionParser(conn);
        return response;
    }

    // Kite wraps every response as {"status": "...", "data": ...}; return only the data part
    public String getData(String path, String accessToken) throws Exception {
        return new JSONObject(get(path, accessToken)).get("data").toString();
    }

    // For endpoints that return a file instead of JSON (e.g. /instruments is a CSV of ~100k rows).
    // Streams the body straight to disk so the whole file is never held in memory.
    public Path downloadTo(String path, String accessToken, Path target) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Kite-Version", KITE_VERSION);
        conn.setRequestProperty(
                "Authorization",
                "token " + props.getApiKey() + ":" + accessToken
        );
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(120_000);

        try {
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                connectionParser(conn);   // reads the error body and throws with it
            }

            Files.createDirectories(target.getParent());
            // Write to a temp file first, then swap it in, so a failed download never leaves a half-written CSV
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (InputStream input = conn.getInputStream()) {
                Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {
            conn.disconnect();
        }
    }

    public String connectionParser(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String response = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (status >= 200 && status < 300) {
            return response;
        } else {
            throw new RuntimeException("API call failed: " + status + " " + response);
        }
    }

}