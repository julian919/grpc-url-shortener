package com.example.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.user.api.RegisterRequest;
import com.example.user.api.RegisterResponse;
import com.example.user.profile.ProfileService;
import com.example.user.registration.RegistrationService;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserGrpcServiceTest {

  @Mock
  private RegistrationService registrationService;
  @Mock
  private ProfileService profileService;

  @Mock
  private StreamObserver<RegisterResponse> responseObserver;

  private UserGrpcService service;

  @BeforeEach
  void setUp() {
    service = new UserGrpcService(registrationService, profileService);
  }

  @Test
  void register_success_delegatesToRegistrationServiceAndCompletes() {
    UUID principalId = UUID.randomUUID();
    when(registrationService.register("user@example.com", "secret123", "Ada", "Lovelace")).thenReturn(principalId);

    RegisterRequest request = RegisterRequest.newBuilder()
        .setEmail("user@example.com")
        .setPassword("secret123")
        .setFirstName("Ada")
        .setLastName("Lovelace")
        .build();

    service.register(request, responseObserver);

    ArgumentCaptor<RegisterResponse> captor = ArgumentCaptor.forClass(RegisterResponse.class);
    verify(responseObserver).onNext(captor.capture());
    assertThat(captor.getValue().getPrincipalId()).isEqualTo(principalId.toString());

    verify(responseObserver).onCompleted();
    verify(registrationService).register("user@example.com", "secret123", "Ada", "Lovelace");
  }
}
