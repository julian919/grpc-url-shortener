package com.example.auth.exception;

/** Deliberately the same message whether the email doesn't exist or the password is wrong --
 * distinguishing the two would tell a caller which emails are registered. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid email or password");
  }
}
