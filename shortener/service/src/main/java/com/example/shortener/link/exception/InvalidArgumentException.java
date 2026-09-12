package com.example.shortener.link.exception;

/**
 * The request failed validation before any downstream call was made.
 *
 * <p>Public so it can be thrown from com.example.shortener.grpc and caught here by
 * {@link ShortenerExceptionAdvice} -- a package-private exception can't cross that boundary.
 */
public class InvalidArgumentException extends RuntimeException {

  public InvalidArgumentException(String message) {
    super(message);
  }
}
