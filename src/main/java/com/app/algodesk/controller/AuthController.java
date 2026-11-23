package com.app.algodesk.controller;

import com.app.algodesk.appsetup.FieldConstants;
import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.UserAuthService;
import com.app.algodesk.service.InstrumentDataService;
import com.app.algodesk.service.MarketDataService;
import com.app.algodesk.broker.kite.KiteApiClient;
import com.app.algodesk.entity.AuthTokenEntity;
import com.app.algodesk.utils.ApiClientUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@Scope("request")
public class AuthController {

    @Autowired
    UserAuthService userAuthService;

    @Autowired
    MarketDataService marketDataService;

    @Autowired
    InstrumentDataService instrumentDataService;

    @GetMapping("/login")
    @ResponseBody
    public ResponseEntity<Void> login(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(FieldConstants.FIELD_KIETE_STATE, state);
        String url = userAuthService.getLoginUrl(state);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    @GetMapping("/kite/callback")
    public ResponseEntity<String> callback(String request_token, String state, HttpServletRequest request) {
        HttpSession session = request.getSession();

        // Already logged in (refresh, back button, bookmarked callback URL) -> just go to the dashboard
        if (Boolean.TRUE.equals(session.getAttribute(FieldConstants.FIELD_USER_AUTH))) {
            return redirect("/dashboard");
        }

        String saved = (String) session.getAttribute(FieldConstants.FIELD_KIETE_STATE);
        session.removeAttribute(FieldConstants.FIELD_KIETE_STATE);
        if (saved == null || !saved.equals(state)) {
            // Stale or replayed callback: start a fresh login instead of showing an error
            return redirect("/login");
        }
        if (request_token == null || request_token.isBlank()) {
            return redirect("/login");
        }
        try {
            AuthTokenEntity token = userAuthService.authenticateUser(request_token);

            // New session id after login to prevent session fixation
            request.changeSessionId();
            session.setAttribute(FieldConstants.FIELD_USER_AUTH, true);
            session.setAttribute(FieldConstants.FIELD_USER_ID, token.getUser().getUserId());
            session.setAttribute(FieldConstants.FIELD_TOKEN_EXPIRES_AT, token.getExpiresAt());

            marketDataService.start(token.getAccessToken());
            // Download takes a few seconds; run it in the background so the redirect isn't delayed
            String accessToken = token.getAccessToken();
            CompletableFuture.runAsync(() -> instrumentDataService.refreshIfNeeded(accessToken));
            return redirect("/dashboard");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Authentication failed");
        }
    }

    private static ResponseEntity<String> redirect(String location) {
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Long userId = (Long) session.getAttribute(FieldConstants.FIELD_USER_ID);
            if (userId != null) {
                // Close the socket before the token is killed, so it doesn't retry with a dead token
                marketDataService.stop();
                userAuthService.logout(userId);
            }
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return redirect("/logged-out");
    }
}
