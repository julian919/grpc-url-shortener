package com.example.user.registration;

import com.example.user.exception.InvalidArgumentException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

  private final RegistrationInterface principalDirectory;

  public RegistrationService(RegistrationInterface principalDirectory) {
    this.principalDirectory = principalDirectory;
  }

  public UUID register(String email, String password) {
    if (email == null || email.isBlank()) {
      throw new InvalidArgumentException("email must not be blank");
    }
    if (password == null || password.isBlank()) {
      throw new InvalidArgumentException("password must not be blank");
    }
    return principalDirectory.createPrincipal(email, password);
  }
}
