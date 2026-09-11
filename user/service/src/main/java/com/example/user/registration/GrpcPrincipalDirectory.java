package com.example.user.registration;

import com.example.auth.api.AuthServiceGrpc;
import com.example.auth.api.CreatePrincipalRequest;
import com.example.auth.api.CreatePrincipalResponse;
import com.example.user.registration.exception.RegistrationFailedException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GrpcPrincipalDirectory implements PrincipalDirectory {

  private final AuthServiceGrpc.AuthServiceBlockingStub authStub;

  public GrpcPrincipalDirectory(@Qualifier("authenticated") AuthServiceGrpc.AuthServiceBlockingStub authStub) {
    this.authStub = authStub;
  }

  @Override
  public UUID createPrincipal(String email, String password) {
    CreatePrincipalResponse response;
    try {
      response = authStub.createPrincipal(
          CreatePrincipalRequest.newBuilder().setEmail(email).setPassword(password).build());
    } catch (RuntimeException e) {
      throw new RegistrationFailedException("failed to create principal for " + email, e);
    }
    return UUID.fromString(response.getPrincipalId());
  }
}
