package com.example.shortener.link.exception;

/**
 * link-audit ran and said no. Distinct from {@link AuditUnavailableException}: audit was
 * reachable and gave a verdict here, it just wasn't the verdict we wanted.
 */
public class UrlFlaggedException extends RuntimeException {

  public UrlFlaggedException(String message) {
    super(message);
  }
}
