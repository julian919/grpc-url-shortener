package com.example.auth;

import com.example.auth.api.AuthServiceGrpc;
import com.example.auth.api.CreatePrincipalRequest;
import com.example.auth.api.CreatePrincipalResponse;
import com.example.auth.api.IssueServiceTokenRequest;
import com.example.auth.api.IssueServiceTokenResponse;
import com.example.auth.api.LoginRequest;
import com.example.auth.api.LoginResponse;
import com.example.auth.service.JwtService;
import com.example.auth.service.PrincipalService;
import com.example.auth.user.PrincipalEntity;

import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class AuthService extends AuthServiceGrpc.AuthServiceImplBase {

  private final PrincipalService principalService;
  private final JwtService jwtService;

  public AuthService(PrincipalService principalService, JwtService jwtService) {
    this.principalService = principalService;
    this.jwtService = jwtService;
  }

  @Override
  public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
    PrincipalEntity principal = principalService.authenticate(request.getEmail(), request.getPassword());
    String accessToken = jwtService.issueAccessToken(principal);

    responseObserver.onNext(
        LoginResponse.newBuilder()
            .setAccessToken(accessToken)
            .setExpiresInSeconds(jwtService.accessTokenTtl().toSeconds())
            .build());
    responseObserver.onCompleted();
  }

  @Override
  public void issueServiceToken(
      IssueServiceTokenRequest request, StreamObserver<IssueServiceTokenResponse> responseObserver) {
    PrincipalEntity principal = principalService.authenticateService(request.getClientId(), request.getClientSecret());
    String accessToken = jwtService.issueServiceToken(principal);

    responseObserver.onNext(
        IssueServiceTokenResponse.newBuilder()
            .setAccessToken(accessToken)
            .setExpiresInSeconds(jwtService.serviceTokenTtl().toSeconds())
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
