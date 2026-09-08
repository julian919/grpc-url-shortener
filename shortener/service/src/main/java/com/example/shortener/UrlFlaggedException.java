package com.example.shortener;

/** link-audit ran and said no. Distinct from {@link AuditUnavailableException}: audit was
 * reachable and gave a verdict here, it just wasn't the verdict we wanted. */
class UrlFlaggedException extends RuntimeException {

  UrlFlaggedException(String message) {
    super(message);
  }
}
