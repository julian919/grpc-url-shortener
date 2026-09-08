package com.example.shortener.exception;

/**
 * link-audit ran and said no. Distinct from {@link AuditUnavailableException}: audit was
 * reachable and gave a verdict here, it just wasn't the verdict we wanted.
 *
 * <p>Public for the same reason as {@link InvalidArgumentException} -- thrown from
 * com.example.shortener.grpc, caught here.
 */
public class UrlFlaggedException extends RuntimeException {

  public UrlFlaggedException(String message) {
    super(message);
  }
}
