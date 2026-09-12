package com.example.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.api.AuthServiceGrpc;
import com.example.auth.api.GetClientTokenRequest;
import com.example.auth.api.OAuth2Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Only the gRPC call is mocked -- ServiceTokenSupplier's own caching logic is
 * real, since that's the actual thing worth testing here.
 */
@ExtendWith(MockitoExtension.class)
class ServiceTokenSupplierTest {

  @Mock
  private AuthServiceGrpc.AuthServiceBlockingStub authServiceStub;

  @Test
  void token_secondCallWithinTtl_reusesTheCachedToken() {
    when(authServiceStub.getClientToken(any(GetClientTokenRequest.class)))
        .thenReturn(
            OAuth2Token.newBuilder()
                .setAccessToken("first-token")
                .setTokenType("Bearer")
                .setExpiresInSeconds(300)
                .build());
    ServiceTokenSupplier supplier = new ServiceTokenSupplier(authServiceStub, "user-service", "secret");

    String first = supplier.token();
    String second = supplier.token();

    assertThat(first).isEqualTo("first-token");
    assertThat(second).isEqualTo("first-token");
    // The whole point of caching: one 300s-TTL token should serve two calls made back to
    // back, not trigger a second GetClientToken round trip.
    verify(authServiceStub, times(1)).getClientToken(any(GetClientTokenRequest.class));
  }

  @Test
  void token_alreadyExpired_fetchesAgain() {
    when(authServiceStub.getClientToken(any(GetClientTokenRequest.class)))
        .thenReturn(
            OAuth2Token.newBuilder()
                .setAccessToken("short-lived-token")
                .setTokenType("Bearer")
                .setExpiresInSeconds(0) // expires (within the refresh margin) immediately
                .build())
        .thenReturn(
            OAuth2Token.newBuilder()
                .setAccessToken("second-token")
                .setTokenType("Bearer")
                .setExpiresInSeconds(300)
                .build());
    ServiceTokenSupplier supplier = new ServiceTokenSupplier(authServiceStub, "user-service", "secret");

    String first = supplier.token();
    String second = supplier.token();

    assertThat(first).isEqualTo("short-lived-token");
    assertThat(second).isEqualTo("second-token");
    verify(authServiceStub, times(2)).getClientToken(any(GetClientTokenRequest.class));
  }
}
