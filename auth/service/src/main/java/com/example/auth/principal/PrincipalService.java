package com.example.auth.principal;

import com.example.auth.principal.entity.AuthProviderEnum;
import com.example.auth.principal.entity.LoginEntity;
import com.example.auth.principal.entity.PrincipalEntity;
import com.example.auth.principal.entity.PrincipalTypeEnum;
import com.example.auth.principal.exception.InvalidCredentialsException;
import com.example.auth.principal.exception.LoginAlreadyRegisteredException;
import com.example.auth.principal.repository.LoginRepository;
import com.example.auth.principal.repository.PrincipalRepository;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrincipalService {

  private final PrincipalRepository principalRepository;
  private final LoginRepository loginRepository;
  private final PasswordEncoder passwordEncoder;

  public PrincipalService(
      PrincipalRepository principalRepository,
      LoginRepository loginRepository,
      PasswordEncoder passwordEncoder) {
    this.principalRepository = principalRepository;
    this.loginRepository = loginRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public PrincipalEntity createPrincipal(String email, String plaintextPassword) {
    PrincipalEntity principal = principalRepository
        .save(new PrincipalEntity(passwordEncoder.encode(plaintextPassword), Set.of("USER")));

    try {
      loginRepository.saveAndFlush(new LoginEntity(principal, AuthProviderEnum.EMAIL, email));
    } catch (DataIntegrityViolationException e) {
      // Rolls back the principal too -- @Transactional covers this whole method.
      throw new LoginAlreadyRegisteredException(email);
    }

    return principal;
  }

  @Transactional(readOnly = true)
  public PrincipalEntity authenticate(String email, String plaintextPassword) {
    LoginEntity login = loginRepository
        .findByProviderAndAccountId(AuthProviderEnum.EMAIL, email)
        .orElseThrow(InvalidCredentialsException::new);

    PrincipalEntity principal = login.getPrincipal();
    if (!passwordEncoder.matches(plaintextPassword, principal.getSecretHash())) {
      throw new InvalidCredentialsException();
    }

    return principal;
  }

  @Transactional(readOnly = true)
  public PrincipalEntity authenticateService(String clientId, String clientSecret) {
    LoginEntity login = loginRepository
        .findByProviderAndAccountId(AuthProviderEnum.CLIENT_ID, clientId)
        .orElseThrow(() -> new InvalidCredentialsException("Invalid client credentials"));

    PrincipalEntity principal = login.getPrincipal();
    // Defense in depth: even a CLIENT_ID login row must point at a SERVICE-typed
    // principal --
    // guards against a USER principal ever being reachable through this grant.
    if (principal.getType() != PrincipalTypeEnum.SERVICE
        || !passwordEncoder.matches(clientSecret, principal.getSecretHash())) {
      throw new InvalidCredentialsException("Invalid client credentials");
    }

    return principal;
  }
}
