package com.example.user.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.user.exception.InvalidArgumentException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  @Mock
  private RegistrationInterface principalDirectory;

  @Test
  void register_blankEmail_throwsInvalidArgument() {
    RegistrationService service = new RegistrationService(principalDirectory);

    assertThatThrownBy(() -> service.register("", "password"))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void register_blankPassword_throwsInvalidArgument() {
    RegistrationService service = new RegistrationService(principalDirectory);

    assertThatThrownBy(() -> service.register("user@example.com", " "))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void register_validInput_delegatesToPrincipalDirectory() {
    UUID id = UUID.randomUUID();
    when(principalDirectory.createPrincipal("user@example.com", "password")).thenReturn(id);
    RegistrationService service = new RegistrationService(principalDirectory);

    UUID result = service.register("user@example.com", "password");

    assertThat(result).isEqualTo(id);
    verify(principalDirectory).createPrincipal("user@example.com", "password");
  }
}
