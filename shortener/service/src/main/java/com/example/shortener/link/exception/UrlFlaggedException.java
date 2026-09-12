package com.example.shortener.link.exception;

/**
 * The audit ran and said no -- the URL's host is on the blocklist, so the link is refused rather
 * than stored. Mapped to FAILED_PRECONDITION by {@code ShortenerExceptionAdvice}.
 */
public class UrlFlaggedException extends RuntimeException {

  public UrlFlaggedException(String message) {
    super(message);
  }
}
