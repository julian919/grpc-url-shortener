package com.example.auth.service;

import com.example.auth.entity.AuthProvider;
import com.example.auth.entity.Login;
import com.example.auth.entity.Principal;
import com.example.auth.exception.InvalidCredentialsException;
import com.example.auth.exception.LoginAlreadyRegisteredException;
import com.example.auth.repository.LoginRepository;
import com.example.auth.repository.PrincipalRepository;
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
  public Principal createPrincipal(String email, String plaintextPassword) {
    Principal principal =
        principalRepository.save(new Principal(passwordEncoder.encode(plaintextPassword), Set.of("USER")));

    try {
      loginRepository.saveAndFlush(new Login(principal, AuthProvider.EMAIL, email));
    } catch (DataIntegrityViolationException e) {
      // Rolls back the principal too -- @Transactional covers this whole method.
      throw new LoginAlreadyRegisteredException(email);
    }

    return principal;
  }

  @Transactional(readOnly = true)
  public Principal authenticate(String email, String plaintextPassword) {
    Login login =
        loginRepository
            .findByProviderAndAccountId(AuthProvider.EMAIL, email)
            .orElseThrow(InvalidCredentialsException::new);

    Principal principal = login.getPrincipal();
    if (!passwordEncoder.matches(plaintextPassword, principal.getSecretHash())) {
      throw new InvalidCredentialsException();
    }

    return principal;
  }
}
