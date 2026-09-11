package com.example.user.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.api.AuthServiceGrpc;
import com.example.auth.api.CreatePrincipalRequest;
import com.example.auth.api.CreatePrincipalResponse;
import com.example.user.registration.exception.RegistrationFailedException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcPrincipalDirectoryTest {

  @Mock
  private AuthServiceGrpc.AuthServiceBlockingStub authStub;

  private GrpcPrincipalDirectory directory;

  @BeforeEach
  void setUp() {
    directory = new GrpcPrincipalDirectory(authStub);
  }

  @Test
  void createPrincipal_success_returnsPrincipalUuid() {
    UUID expectedId = UUID.randomUUID();
    when(authStub.createPrincipal(any(CreatePrincipalRequest.class)))
        .thenReturn(CreatePrincipalResponse.newBuilder().setPrincipalId(expectedId.toString()).build());

    UUID actualId = directory.createPrincipal("alice@example.com", "secret123");

    assertThat(actualId).isEqualTo(expectedId);

    ArgumentCaptor<CreatePrincipalRequest> captor = ArgumentCaptor.forClass(CreatePrincipalRequest.class);
    verify(authStub).createPrincipal(captor.capture());
    assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
    assertThat(captor.getValue().getPassword()).isEqualTo("secret123");
  }

  @Test
  void createPrincipal_rpcFails_throwsRegistrationFailedExceptionWithCause() {
    StatusRuntimeException rpcException = new StatusRuntimeException(Status.ALREADY_EXISTS);
    when(authStub.createPrincipal(any(CreatePrincipalRequest.class))).thenThrow(rpcException);

    assertThatThrownBy(() -> directory.createPrincipal("alice@example.com", "secret123"))
        .isInstanceOf(RegistrationFailedException.class)
        .hasMessageContaining("failed to create principal for alice@example.com")
        .hasCause(rpcException);
  }
}
