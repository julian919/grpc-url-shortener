package com.example.auth;

import com.example.auth.api.AuthServiceGrpc;
import com.example.auth.api.CreatePrincipalRequest;
import com.example.auth.api.CreatePrincipalResponse;
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
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

  private final PrincipalService principalService;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;

  public AuthGrpcService(
      PrincipalService principalService,
      JwtService jwtService,
      RefreshTokenService refreshTokenService) {
    this.principalService = principalService;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
  }

  @Override
  public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
    PrincipalEntity principal = principalService.authenticate(request.getEmail(), request.getPassword());
    String accessToken = jwtService.issueAccessToken(principal);
    String refreshToken = refreshTokenService.issueRefreshToken(principal);

    responseObserver.onNext(
        LoginResponse.newBuilder()
            .setAccessToken(accessToken)
            .setExpiresInSeconds(jwtService.accessTokenTtl().toSeconds())
            .setRefreshToken(refreshToken)
            .build());
    responseObserver.onCompleted();
  }

  @Override
  public void getClientToken(
      GetClientTokenRequest request, StreamObserver<OAuth2Token> responseObserver) {
    String clientId = request.getClientId();
    String clientSecret = request.getClientSecret();

    // Fallback to Basic Auth metadata header if not provided in body (Cognixus style)
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      BasicAuthServerInterceptor.ClientCredentials credentials =
          BasicAuthServerInterceptor.CLIENT_CREDENTIALS_KEY.get();
      if (credentials != null) {
        clientId = credentials.clientId();
        clientSecret = credentials.clientSecret();
      }
    }

    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      throw Status.UNAUTHENTICATED
          .withDescription("Client credentials must be provided in request body or Authorization: Basic header")
          .asRuntimeException();
    }

    PrincipalEntity principal = principalService.authenticateService(clientId, clientSecret);
    String accessToken = jwtService.issueServiceToken(principal);

    responseObserver.onNext(
        OAuth2Token.newBuilder()
            .setAccessToken(accessToken)
            .setTokenType("bearer")
            .setExpiresInSeconds(jwtService.serviceTokenTtl().toSeconds())
            .build());
    responseObserver.onCompleted();
  }

  @Override
  public void renewToken(
      RenewTokenRequest request, StreamObserver<RenewTokenResponse> responseObserver) {
    OAuth2Token renewedToken = refreshTokenService.rotateRefreshToken(request.getRefreshToken());

    responseObserver.onNext(
        RenewTokenResponse.newBuilder()
            .setToken(renewedToken)
            .build());
    responseObserver.onCompleted();
  }

  @Override
  public void createPrincipal(
      CreatePrincipalRequest request, StreamObserver<CreatePrincipalResponse> responseObserver) {
    PrincipalEntity principal = principalService.createPrincipal(request.getEmail(), request.getPassword());

    responseObserver.onNext(
        CreatePrincipalResponse.newBuilder().setPrincipalId(principal.getId().toString()).build());
    responseObserver.onCompleted();
  }
}
