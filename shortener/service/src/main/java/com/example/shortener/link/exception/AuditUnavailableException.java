package com.example.shortener.link.exception;

/**
 * link-audit could not be reached or reported its own error -- no verdict came back at all.
 */
public class AuditUnavailableException extends RuntimeException {

  public AuditUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
