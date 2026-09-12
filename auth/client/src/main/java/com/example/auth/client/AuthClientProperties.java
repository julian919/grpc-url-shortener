package com.example.auth.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The credential this service presents to auth-service. Deliberately has NO defaults: an
 * environment that forgot to supply a secret should fail at startup rather than fail later with
 * "Invalid client credentials".
 */
@ConfigurationProperties("app.auth-client")
public record AuthClientProperties(String clientId, String clientSecret) {
}
