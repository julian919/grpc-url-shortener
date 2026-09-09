package com.example.auth.grpc;

import com.example.auth.api.AuthServiceGrpc;
import com.example.auth.api.CreatePrincipalRequest;
import com.example.auth.api.CreatePrincipalResponse;
import com.example.auth.api.LoginRequest;
import com.example.auth.api.LoginResponse;
import com.example.auth.entity.Principal;
import com.example.auth.service.JwtService;
import com.example.auth.service.PrincipalService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

  private final PrincipalService principalService;
  private final JwtService jwtService;

  public AuthGrpcService(PrincipalService principalService, JwtService jwtService) {
    this.principalService = principalService;
    this.jwtService = jwtService;
  }

  @Override
  public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
    Principal principal = principalService.authenticate(request.getEmail(), request.getPassword());
    String accessToken = jwtService.issueAccessToken(principal);

    responseObserver.onNext(
        LoginResponse.newBuilder()
            .setAccessToken(accessToken)
            .setExpiresInSeconds(jwtService.accessTokenTtl().toSeconds())
            .build());
    responseObserver.onCompleted();
  }

  @Override
  public void createPrincipal(
      CreatePrincipalRequest request, StreamObserver<CreatePrincipalResponse> responseObserver) {
    Principal principal = principalService.createPrincipal(request.getEmail(), request.getPassword());

    responseObserver.onNext(
        CreatePrincipalResponse.newBuilder().setPrincipalId(principal.getId().toString()).build());
    responseObserver.onCompleted();
  }
}
