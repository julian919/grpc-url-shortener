package com.example.user.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.user.registration.exception.InvalidArgumentException;
import com.example.user.shared.entity.UserEntity;
import com.example.user.shared.entity.UserStatusEnum;
import com.example.user.shared.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  @Mock
  private PrincipalDirectory principalDirectory;
  @Mock
  private UserRepository userRepository;

  private RegistrationService service;

  @BeforeEach
  void setUp() {
    service = new RegistrationService(principalDirectory, userRepository);
  }

  @Test
  void register_blankEmail_throwsInvalidArgument() {
    assertThatThrownBy(() -> service.register("", "password", "Ada", "Lovelace"))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void register_blankPassword_throwsInvalidArgument() {
    assertThatThrownBy(() -> service.register("user@example.com", " ", "Ada", "Lovelace"))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void register_blankFirstName_throwsInvalidArgument() {
    assertThatThrownBy(() -> service.register("user@example.com", "password", " ", "Lovelace"))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void register_blankLastName_throwsInvalidArgument() {
    assertThatThrownBy(() -> service.register("user@example.com", "password", "Ada", ""))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void register_validationFailure_neverTouchesAuthOrTheDatabase() {
    assertThatThrownBy(() -> service.register("", "password", "Ada", "Lovelace"))
        .isInstanceOf(InvalidArgumentException.class);

    verifyNoInteractions(principalDirectory, userRepository);
  }

  @Test
  void register_createsThePrincipalFirstThenStoresTheProfileUnderItsId() {
    UUID principalId = UUID.randomUUID();
    when(principalDirectory.createPrincipal("user@example.com", "password")).thenReturn(principalId);

    UUID result = service.register("user@example.com", "password", "Ada", "Lovelace");

    assertThat(result).isEqualTo(principalId);
    verify(principalDirectory).createPrincipal("user@example.com", "password");

    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(captor.capture());
    UserEntity saved = captor.getValue();

    // This service mints its OWN key; the identity auth minted is carried as a reference.
    assertThat(saved.getId()).isNotNull().isNotEqualTo(principalId);
    assertThat(saved.getPrincipalId()).isEqualTo(principalId);
    assertThat(saved.getFirstName()).isEqualTo("Ada");
    assertThat(saved.getLastName()).isEqualTo("Lovelace");
    assertThat(saved.getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
    assertThat(saved.isActive()).isTrue();
  }

  @Test
  void register_authFails_noProfileRowIsWritten() {
    when(principalDirectory.createPrincipal(any(), any()))
        .thenThrow(new IllegalStateException("auth-service is down"));

    assertThatThrownBy(() -> service.register("user@example.com", "password", "Ada", "Lovelace"))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(userRepository);
  }
}
