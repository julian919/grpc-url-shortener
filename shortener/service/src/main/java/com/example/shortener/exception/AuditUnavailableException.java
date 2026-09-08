package com.example.shortener.exception;

/**
 * link-audit could not be reached or reported its own error -- no verdict came back at all.
 *
 * <p>Public for the same reason as {@link InvalidArgumentException} -- thrown from
 * com.example.shortener.grpc, caught here.
 */
public class AuditUnavailableException extends RuntimeException {

  public AuditUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
