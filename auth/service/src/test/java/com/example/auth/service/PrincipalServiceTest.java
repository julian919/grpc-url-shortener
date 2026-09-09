package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.Login;
import com.example.auth.entity.Principal;
import com.example.auth.exception.InvalidCredentialsException;
import com.example.auth.exception.LoginAlreadyRegisteredException;
import com.example.auth.repository.LoginRepository;
import com.example.auth.repository.PrincipalRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The password encoder is real (BCrypt), not mocked -- it's pure, deterministic-enough logic
 * with no I/O, same principle as keeping ShortCodeGenerator real in ShortenerGrpcServiceTest.
 * Only the repositories (real DB access) are mocked.
 */
@ExtendWith(MockitoExtension.class)
class PrincipalServiceTest {

  @Mock private PrincipalRepository principalRepository;
  @Mock private LoginRepository loginRepository;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // low cost, fast tests

  private PrincipalService service;

  @BeforeEach
  void setUp() {
    service = new PrincipalService(principalRepository, loginRepository, passwordEncoder);
  }

  @Test
  void createPrincipal_hashesThePasswordAndDefaultsToUserRole() {
    when(principalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.createPrincipal("new@example.com", "correct horse battery staple");

    ArgumentCaptor<Principal> captor = ArgumentCaptor.forClass(Principal.class);
    verify(principalRepository).save(captor.capture());
    Principal saved = captor.getValue();

    assertThat(saved.getSecretHash()).isNotEqualTo("correct horse battery staple");
    assertThat(passwordEncoder.matches("correct horse battery staple", saved.getSecretHash())).isTrue();
    assertThat(saved.getRoles()).containsExactly("USER");
  }

  @Test
  void createPrincipal_duplicateEmail_throwsLoginAlreadyRegistered() {
    when(loginRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.createPrincipal("taken@example.com", "whatever"))
        .isInstanceOf(LoginAlreadyRegisteredException.class);
  }

  @Test
  void authenticate_unknownEmail_throwsInvalidCredentials() {
    when(loginRepository.findByProviderAndAccountId(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.authenticate("nobody@example.com", "whatever"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void authenticate_wrongPassword_throwsInvalidCredentials() {
    Principal principal = new Principal(passwordEncoder.encode("the-real-password"), Set.of("USER"));
    Login login = new Login(principal, com.example.auth.entity.AuthProvider.EMAIL, "user@example.com");
    when(loginRepository.findByProviderAndAccountId(any(), any())).thenReturn(Optional.of(login));

    assertThatThrownBy(() -> service.authenticate("user@example.com", "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void authenticate_correctCredentials_returnsThePrincipal() {
    Principal principal = new Principal(passwordEncoder.encode("the-real-password"), Set.of("USER"));
    Login login = new Login(principal, com.example.auth.entity.AuthProvider.EMAIL, "user@example.com");
    when(loginRepository.findByProviderAndAccountId(any(), any())).thenReturn(Optional.of(login));

    Principal result = service.authenticate("user@example.com", "the-real-password");

    assertThat(result).isSameAs(principal);
  }
}
