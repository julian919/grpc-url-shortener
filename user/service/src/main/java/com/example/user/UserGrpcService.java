package com.example.user;

import com.example.user.api.RegisterRequest;
import com.example.user.api.RegisterResponse;
import com.example.user.api.UserServiceGrpc;
import com.example.user.api.GetUserRequest;
import com.example.user.api.GetUserResponse;
import com.example.user.profile.ProfileService;
import com.example.user.registration.RegistrationService;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

  private final RegistrationService registrationService;
  private final ProfileService profileService;

  public UserGrpcService(RegistrationService registrationService, ProfileService profileService) {
    this.registrationService = registrationService;
    this.profileService = profileService;
  }

  @Override
  public void getUser(GetUserRequest request, StreamObserver<GetUserResponse> responseObserver) {
    responseObserver.onNext(
        GetUserResponse.newBuilder()
            .setUser(profileService.getByPrincipalId(request.getPrincipalId()).toProto())
            .build());
    responseObserver.onCompleted();
  }

  @Override
  public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
    UUID principalId = registrationService.register(
            request.getEmail(),
            request.getPassword(),
            request.getFirstName(),
            request.getLastName());

    responseObserver.onNext(RegisterResponse.newBuilder().setPrincipalId(principalId.toString()).build());
    responseObserver.onCompleted();
  }
}
