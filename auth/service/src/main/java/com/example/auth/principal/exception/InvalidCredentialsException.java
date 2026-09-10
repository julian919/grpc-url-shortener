package com.example.auth.principal.exception;

/**
 * Deliberately the same message for every failure reason within a given grant (unknown
 * identifier vs wrong secret) -- distinguishing them would tell a caller which identifiers
 * are registered. The message itself still varies BETWEEN grants (email login vs client
 * credentials), since "invalid email or password" would be a confusing thing to see back from
 * a client-credentials call that never sent an email at all.
 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    this("Invalid email or password");
  }

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
