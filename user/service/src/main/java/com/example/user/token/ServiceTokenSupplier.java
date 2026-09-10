package com.example.user.token;

import com.example.auth.api.AuthServiceGrpc;
import com.example.auth.api.IssueServiceTokenRequest;
import com.example.auth.api.IssueServiceTokenResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.grpc.client.interceptor.security.TokenSupplier;
import org.springframework.stereotype.Component;

/**
 * Fetches and caches a service access token from auth-service's
 * IssueServiceToken RPC. Must be
 * constructed with the PLAIN, unintercepted stub -- calling IssueServiceToken
 * through the
 * authenticated stub would be circular (a token is needed to get a token).
 */
@Component
public class ServiceTokenSupplier implements TokenSupplier {

  // Refetch this far ahead of actual expiry, so a token already in flight on a
  // call never
  // expires mid-call.
  private static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);

  private final AuthServiceGrpc.AuthServiceBlockingStub authServiceStub;
  private final String clientId;
  private final String clientSecret;

  private String cachedToken;
  private Instant expiresAt = Instant.MIN;

  public ServiceTokenSupplier(
      @Qualifier("plain") AuthServiceGrpc.AuthServiceBlockingStub authServiceStub,
      @Value("${app.auth-client.client-id}") String clientId,
      @Value("${app.auth-client.client-secret}") String clientSecret) {
    this.authServiceStub = authServiceStub;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  @Override
  public synchronized String token() {
    if (cachedToken == null || Instant.now().isAfter(expiresAt.minus(REFRESH_MARGIN))) {
      IssueServiceTokenResponse response = authServiceStub.issueServiceToken(
          IssueServiceTokenRequest.newBuilder()
              .setClientId(clientId)
              .setClientSecret(clientSecret)
              .build());
      cachedToken = response.getAccessToken();
      expiresAt = Instant.now().plusSeconds(response.getExpiresInSeconds());
    }
    return cachedToken;
  }
}
