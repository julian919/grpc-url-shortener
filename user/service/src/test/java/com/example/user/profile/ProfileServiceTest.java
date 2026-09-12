package com.example.user.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.user.shared.entity.UserEntity;
import com.example.user.shared.repository.UserRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock
  private UserRepository userRepository;

  private ProfileService service;

  @BeforeEach
  void setUp() {
    service = new ProfileService(userRepository);
  }

  @Test
  void getByPrincipalId_found_returnsTheUser() {
    UUID principalId = UUID.randomUUID();
    UserEntity entity = new UserEntity(principalId, "Ada", "Lovelace", Instant.ofEpochMilli(1000L));
    when(userRepository.findByPrincipalId(principalId)).thenReturn(Optional.of(entity));

    assertThat(service.getByPrincipalId(principalId.toString())).isSameAs(entity);
  }

  @Test
  void getByPrincipalId_unknown_throwsNotFound() {
    UUID principalId = UUID.randomUUID();
    when(userRepository.findByPrincipalId(principalId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByPrincipalId(principalId.toString()))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(Status.fromThrowable(e).getCode()).isEqualTo(Status.Code.NOT_FOUND));
  }

  @Test
  void getByPrincipalId_notAUuid_throwsInvalidArgument() {
    // Reaches the service as a path segment, so it can be any string at all.
    assertThatThrownBy(() -> service.getByPrincipalId("not-a-uuid"))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(e -> assertThat(Status.fromThrowable(e).getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT));
  }
}
