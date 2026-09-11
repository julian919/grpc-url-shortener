package com.example.user;

import com.example.user.exception.InvalidArgumentException;
import com.example.user.registration.exception.RegistrationFailedException;
import io.grpc.Status;
import org.springframework.grpc.server.advice.GrpcAdvice;
import org.springframework.grpc.server.advice.GrpcExceptionHandler;

/**
 * Same mechanism as shortener-service's ShortenerExceptionAdvice -- class and methods MUST
 * be public, see lessons/0007c-exceptions-and-grpc-status-codes.html Section 6.
 */
@GrpcAdvice
public class UserExceptionAdvice {

  @GrpcExceptionHandler
  public Status handleInvalidArgument(InvalidArgumentException e) {
    return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
  }

  @GrpcExceptionHandler
  public Status handleRegistrationFailed(RegistrationFailedException e) {
    return Status.fromThrowable(e.getCause()).withDescription(e.getMessage());
  }
}
