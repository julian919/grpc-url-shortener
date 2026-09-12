package com.example.auth.principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.principal.entity.AuthProviderEnum;
import com.example.auth.principal.entity.LoginEntity;
import com.example.auth.principal.entity.PrincipalEntity;
import com.example.auth.principal.entity.PrincipalTypeEnum;
import com.example.auth.principal.exception.InvalidCredentialsException;
import com.example.auth.principal.exception.LoginAlreadyRegisteredException;
import com.example.auth.principal.repository.LoginRepository;
import com.example.auth.principal.repository.PrincipalRepository;
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
 * The password encoder is real (BCrypt), not mocked -- it's pure,
 * deterministic-enough logic
 * with no I/O, same principle as keeping ShortCodeGenerator real in
 * ShortenerGrpcServiceTest.
 * Only the repositories (real DB access) are mocked.
 */
@ExtendWith(MockitoExtension.class)
class PrincipalServiceTest {

  @Mock
  private PrincipalRepository principalRepository;
  @Mock
  private LoginRepository loginRepository;

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

    ArgumentCaptor<PrincipalEntity> captor = ArgumentCaptor.forClass(PrincipalEntity.class);
    verify(principalRepository).save(captor.capture());
    PrincipalEntity saved = captor.getValue();

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
    PrincipalEntity principal = new PrincipalEntity(passwordEncoder.encode("the-real-password"), Set.of("USER"));
    LoginEntity login = new LoginEntity(principal, AuthProviderEnum.EMAIL, "user@example.com");
    when(loginRepository.findByProviderAndAccountId(any(), any())).thenReturn(Optional.of(login));

    assertThatThrownBy(() -> service.authenticate("user@example.com", "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void authenticate_correctCredentials_returnsThePrincipal() {
    PrincipalEntity principal = new PrincipalEntity(passwordEncoder.encode("the-real-password"), Set.of("USER"));
    LoginEntity login = new LoginEntity(principal, AuthProviderEnum.EMAIL, "user@example.com");
    when(loginRepository.findByProviderAndAccountId(any(), any())).thenReturn(Optional.of(login));

    PrincipalEntity result = service.authenticate("user@example.com", "the-real-password");

    assertThat(result).isSameAs(principal);
  }

  @Test
  void authenticateService_unknownClientId_throwsInvalidCredentials() {
    when(loginRepository.findByProviderAndAccountId(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.authenticateService("nobody", "whatever"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void authenticateService_wrongSecret_throwsInvalidCredentials() {
    PrincipalEntity principal = new PrincipalEntity(passwordEncoder.encode("the-real-secret"),
        Set.of("USER_SERVICE"),
        PrincipalTypeEnum.SERVICE);
    LoginEntity login = new LoginEntity(principal, AuthProviderEnum.CLIENT_ID, "user-service");
    when(loginRepository.findByProviderAndAccountId(any(), any())).thenReturn(Optional.of(login));

    assertThatThrownBy(() -> service.authenticateService("user-service", "wrong-secret"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void authenticateService_loginPointsAtAUserPrincipal_throwsInvalidCredentials() {
    // Defense in depth: a CLIENT_ID login row must point at a SERVICE-typed
    // principal.
    // Even the RIGHT secret should be rejected if that invariant is somehow
    // violated.
    PrincipalEntity principal = new PrincipalEntity(passwordEncoder.encode("the-real-secret"), Set.of("USER"),
        PrincipalTypeEnum.USER);
    LoginEntity login = new LoginEntity(principal, AuthProviderEnum.CLIENT_ID, "user-service");
    when(loginRepository.findByProviderAndAccountId(any(), any())).thenReturn(Optional.of(login));

    assertThatThrownBy(() -> service.authenticateService("user-service", "the-real-secret"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void authenticateService_correctCredentials_returnsThePrincipal() {
    PrincipalEntity principal = new PrincipalEntity(passwordEncoder.encode("the-real-secret"),
        Set.of("USER_SERVICE"),
        PrincipalTypeEnum.SERVICE);
    LoginEntity login = new LoginEntity(principal, AuthProviderEnum.CLIENT_ID, "user-service");
    when(loginRepository.findByProviderAndAccountId(any(), any())).thenReturn(Optional.of(login));

    PrincipalEntity result = service.authenticateService("user-service", "the-real-secret");

    assertThat(result).isSameAs(principal);
  }
}
