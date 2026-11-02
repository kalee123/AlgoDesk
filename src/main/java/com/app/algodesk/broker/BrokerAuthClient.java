package com.app.algodesk.broker;

public interface BrokerAuthClient {

    String getLoginUrl(String state);

    BrokerSession createSession(String requestToken) throws Exception;

    String getApiKey();

    // Tells the broker to kill the access token so it can't be used again
    void invalidateSession(String accessToken) throws Exception;
}
