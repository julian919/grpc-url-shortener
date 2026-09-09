package com.example.user.exception;

/** Wraps whatever auth-service's CreatePrincipal call threw -- the cause is what
 * UserExceptionAdvice reads to preserve the ORIGINAL Status code (ALREADY_EXISTS,
 * UNAVAILABLE, ...) rather than collapsing every downstream failure into one code. */
public class RegistrationFailedException extends RuntimeException {

  public RegistrationFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
