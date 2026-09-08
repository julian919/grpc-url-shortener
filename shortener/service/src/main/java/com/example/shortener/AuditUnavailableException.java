package com.example.shortener;

/** link-audit could not be reached or reported its own error -- no verdict came back at all. */
class AuditUnavailableException extends RuntimeException {

  AuditUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
