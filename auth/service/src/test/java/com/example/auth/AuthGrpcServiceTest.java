package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.auth.api.GetClientTokenRequest;
import com.example.auth.api.LoginRequest;
import com.example.auth.api.LoginResponse;
import com.example.auth.api.OAuth2Token;
import com.example.auth.api.RenewTokenRequest;
import com.example.auth.api.RenewTokenResponse;
import com.example.auth.interceptor.BasicAuthServerInterceptor;
import com.example.auth.principal.PrincipalService;
import com.example.auth.principal.entity.PrincipalEntity;
import com.example.auth.token.JwtService;
import com.example.auth.token.RefreshTokenService;
import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthGrpcServiceTest {

  @Mock
  private PrincipalService principalService;

  @Mock
  private JwtService jwtService;

  @Mock
  private RefreshTokenService refreshTokenService;

  private AuthGrpcService authService;

  private PrincipalEntity userPrincipal;
  private PrincipalEntity servicePrincipal;

  @BeforeEach
  void setUp() {
    authService = new AuthGrpcService(principalService, jwtService, refreshTokenService);
    userPrincipal = new PrincipalEntity("user-hash", Set.of("USER"));
    servicePrincipal = new PrincipalEntity("service-hash", Set.of("SERVICE_INTERNAL"));
  }

  @Test
  void login_success_returnsAccessTokenAndRefreshToken() {
    when(principalService.authenticate("test@example.com", "pass123")).thenReturn(userPrincipal);
    when(jwtService.issueAccessToken(userPrincipal)).thenReturn("user-access-token");
    when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
    when(refreshTokenService.issueRefreshToken(userPrincipal)).thenReturn("user-refresh-token");

    AtomicReference<LoginResponse> responseRef = new AtomicReference<>();
    AtomicBoolean completed = new AtomicBoolean(false);

    authService.login(
        LoginRequest.newBuilder().setEmail("test@example.com").setPassword("pass123").build(),
        new StreamObserver<>() {
          @Override
          public void onNext(LoginResponse value) {
            responseRef.set(value);
          }

          @Override
          public void onError(Throwable t) {}

          @Override
          public void onCompleted() {
            completed.set(true);
          }
        });

    assertThat(completed.get()).isTrue();
    assertThat(responseRef.get()).isNotNull();
    assertThat(responseRef.get().getAccessToken()).isEqualTo("user-access-token");
    assertThat(responseRef.get().getExpiresInSeconds()).isEqualTo(900);
    assertThat(responseRef.get().getRefreshToken()).isEqualTo("user-refresh-token");
  }

  @Test
  void getClientToken_withBodyCredentials_success() {
    when(principalService.authenticateService("user-service", "secret")).thenReturn(servicePrincipal);
    when(jwtService.issueServiceToken(servicePrincipal)).thenReturn("service-token");
    when(jwtService.serviceTokenTtl()).thenReturn(Duration.ofMinutes(5));

    AtomicReference<OAuth2Token> responseRef = new AtomicReference<>();
    authService.getClientToken(
        GetClientTokenRequest.newBuilder()
            .setClientId("user-service")
            .setClientSecret("secret")
            .build(),
        simpleObserver(responseRef));

    assertThat(responseRef.get()).isNotNull();
    assertThat(responseRef.get().getAccessToken()).isEqualTo("service-token");
    assertThat(responseRef.get().getTokenType()).isEqualTo("bearer");
    assertThat(responseRef.get().getExpiresInSeconds()).isEqualTo(300);
  }

  @Test
  void getClientToken_withBasicAuthMetadataContext_success() throws Exception {
    when(principalService.authenticateService("user-service", "secret")).thenReturn(servicePrincipal);
    when(jwtService.issueServiceToken(servicePrincipal)).thenReturn("service-token");
    when(jwtService.serviceTokenTtl()).thenReturn(Duration.ofMinutes(5));

    AtomicReference<OAuth2Token> responseRef = new AtomicReference<>();

    Context ctx = Context.current().withValue(
        BasicAuthServerInterceptor.CLIENT_CREDENTIALS_KEY,
        new BasicAuthServerInterceptor.ClientCredentials("user-service", "secret"));

    ctx.run(() -> {
      // Empty request body -- credentials pulled from context
      authService.getClientToken(GetClientTokenRequest.getDefaultInstance(), simpleObserver(responseRef));
    });

    assertThat(responseRef.get()).isNotNull();
    assertThat(responseRef.get().getAccessToken()).isEqualTo("service-token");
  }

  @Test
  void getClientToken_missingCredentials_throwsUnauthenticated() {
    assertThatThrownBy(() -> authService.getClientToken(
        GetClientTokenRequest.getDefaultInstance(),
        simpleObserver(new AtomicReference<>())))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("Client credentials must be provided");
  }

  @Test
  void renewToken_success_rotatesRefreshToken() {
    OAuth2Token rotatedToken = OAuth2Token.newBuilder()
        .setAccessToken("new-access-token")
        .setTokenType("bearer")
        .setExpiresInSeconds(900)
        .setRefreshToken("new-refresh-token")
        .build();

    when(refreshTokenService.rotateRefreshToken("old-refresh-token")).thenReturn(rotatedToken);

    AtomicReference<RenewTokenResponse> responseRef = new AtomicReference<>();
    authService.renewToken(
        RenewTokenRequest.newBuilder().setRefreshToken("old-refresh-token").build(),
        simpleObserver(responseRef));

    assertThat(responseRef.get()).isNotNull();
    assertThat(responseRef.get().getToken().getAccessToken()).isEqualTo("new-access-token");
    assertThat(responseRef.get().getToken().getRefreshToken()).isEqualTo("new-refresh-token");
  }

  private <T> StreamObserver<T> simpleObserver(AtomicReference<T> ref) {
    return new StreamObserver<>() {
      @Override
      public void onNext(T value) {
        ref.set(value);
      }

      @Override
      public void onError(Throwable t) {}

      @Override
      public void onCompleted() {}
    };
  }
}
