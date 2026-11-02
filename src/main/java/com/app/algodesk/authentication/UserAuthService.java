package com.app.algodesk.authentication;

import com.app.algodesk.broker.BrokerAuthClient;
import com.app.algodesk.broker.BrokerSession;
import com.app.algodesk.entity.AuthTokenEntity;
import com.app.algodesk.entity.UserEntity;
import com.app.algodesk.repository.AuthTokenRepository;
import com.app.algodesk.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserAuthService {

    private final BrokerAuthClient brokerAuthClient;
    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;

    public UserAuthService(BrokerAuthClient brokerAuthClient,
                           UserRepository userRepository,
                           AuthTokenRepository authTokenRepository) {
        this.brokerAuthClient = brokerAuthClient;
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
    }

    public String getLoginUrl(String state) {
        return brokerAuthClient.getLoginUrl(state);
    }

    /**
     * Exchanges the broker request_token for an access token and persists it.
     * Returns the stored token so the caller can set up the user session.
     */
    @Transactional
    public AuthTokenEntity authenticateUser(String requestToken) {
        BrokerSession session;
        try {
            session = brokerAuthClient.createSession(requestToken);
        } catch (Exception e) {
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
        return saveSession(session);
    }

    /**
     * Kills the user's active broker tokens at the broker and marks them inactive here.
     * The local deactivation always happens, even if the broker call fails, so the app
     * never keeps using a token the user asked to log out of.
     */
    @Transactional
    public void logout(Long userId) {
        for (AuthTokenEntity token : authTokenRepository.findByUser_UserIdAndActiveTrue(userId)) {
            try {
                brokerAuthClient.invalidateSession(token.getAccessToken());
            } catch (Exception e) {
                // Already expired at the broker, or broker unreachable: nothing more we can do
            }
            token.setActive(false);
        }
    }

    private AuthTokenEntity saveSession(BrokerSession session) {
        // Upsert the user: same broker user id logging in again reuses the existing row
        UserEntity user = userRepository.findByUserBrokerId(session.userBrokerId())
                .orElseGet(UserEntity::new);
        user.setUserBrokerId(session.userBrokerId());
        user.setUserName(session.userName());
        user.setEmail(session.email());
        user.setBroker(session.broker());

        // Only one active token per user; older ones are kept for audit but deactivated
        user.getAuthTokens().forEach(t -> t.setActive(false));

        AuthTokenEntity token = new AuthTokenEntity();
        token.setAccessToken(session.accessToken());
        token.setApiKey(brokerAuthClient.getApiKey());
        token.setExpiresAt(session.expiresAt());
        user.addAuthToken(token);

        userRepository.save(user);
        return token;
    }

    /** Active, non-expired access token for API calls on behalf of the user. */
    @Transactional(readOnly = true)
    public Optional<String> getActiveAccessToken(Long userId) {
        return authTokenRepository.findTopByUser_UserIdAndActiveTrueOrderByIssuedAtDesc(userId)
                .filter(t -> !t.isExpired())
                .map(AuthTokenEntity::getAccessToken);
    }
}
