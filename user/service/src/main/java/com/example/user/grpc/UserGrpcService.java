package com.example.user.grpc;

import com.example.user.api.RegisterRequest;
import com.example.user.api.RegisterResponse;
import com.example.user.api.UserServiceGrpc;
import com.example.user.registration.RegistrationService;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

  private final RegistrationService registrationService;

  public UserGrpcService(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @Override
  public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
    UUID principalId = registrationService.register(request.getEmail(), request.getPassword());

    responseObserver.onNext(RegisterResponse.newBuilder().setPrincipalId(principalId.toString()).build());
    responseObserver.onCompleted();
  }
}
