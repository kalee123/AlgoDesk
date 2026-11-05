package com.app.algodesk.authentication;

import com.app.algodesk.appsetup.FieldConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    // Reachable without logging in (also used by SecurityConfig)
    static final String[] PUBLIC_PATHS = {"/login", "/kite/callback", "/logged-out", "/favicon.svg"};

    private final RqInfo rqInfo;

    public SessionAuthFilter(RqInfo rqInfo) {
        this.rqInfo = rqInfo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        HttpSession session = request.getSession(false);
        boolean auth = isAuthenticated(session);

        if ("/".equals(path)) {
            response.sendRedirect(auth ? "/dashboard" : "/login");
            return;
        }

        if ("/login".equals(path) && auth) {
            response.sendRedirect("/dashboard");
            return;
        }

        if (List.of(PUBLIC_PATHS).contains(path) || path.startsWith("/css/")) {
            chain.doFilter(request, response);
            return;
        }

        if (!auth) {
            // API calls come from page scripts, which can't follow a redirect to Kite's login page
            if (path.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                response.sendRedirect("/login");
            }
            return;
        }

        // Tell Spring Security who the caller is, otherwise .authenticated() rejects the request
        Long userId = (Long) session.getAttribute(FieldConstants.FIELD_USER_ID);
        rqInfo.setUserId(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        chain.doFilter(request, response);
    }

    private boolean isAuthenticated(HttpSession session) {
        if (session == null) {
            return false;
        }
        Boolean auth = (Boolean) session.getAttribute(FieldConstants.FIELD_USER_AUTH);
        if (auth == null || !auth || session.getAttribute(FieldConstants.FIELD_USER_ID) == null) {
            return false;
        }
        // Broker token expired (Kite resets daily at 6 AM IST) -> force a fresh login
        LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(FieldConstants.FIELD_TOKEN_EXPIRES_AT);
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            session.invalidate();
            return false;
        }
        return true;
    }
}
