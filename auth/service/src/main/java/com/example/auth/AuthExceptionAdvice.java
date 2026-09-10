package com.example.auth;

import com.example.auth.principal.exception.InvalidCredentialsException;
import com.example.auth.principal.exception.LoginAlreadyRegisteredException;
import io.grpc.Status;
import org.springframework.grpc.server.advice.GrpcAdvice;
import org.springframework.grpc.server.advice.GrpcExceptionHandler;

/**
 * Same mechanism as shortener-service's {@code ShortenerExceptionAdvice} -- see
 * lessons/0007c-exceptions-and-grpc-status-codes.html for how this was found and verified.
 *
 * <p>Class and methods MUST be {@code public}: Spring's reflective resolver silently falls
 * back to {@code Status.INTERNAL} for a package-private handler -- a real bug this project hit
 * once already, see the same lesson's Section 6.
 */
@GrpcAdvice
public class AuthExceptionAdvice {

  @GrpcExceptionHandler
  public Status handleInvalidCredentials(InvalidCredentialsException e) {
    return Status.UNAUTHENTICATED.withDescription(e.getMessage());
  }

  @GrpcExceptionHandler
  public Status handleAlreadyRegistered(LoginAlreadyRegisteredException e) {
    return Status.ALREADY_EXISTS.withDescription(e.getMessage());
  }
}
