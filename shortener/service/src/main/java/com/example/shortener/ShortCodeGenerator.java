package com.example.shortener;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Counter-block base62 key generation.
 *
 * <p>Chosen over random-and-retry because the write path stays a pure insert: no
 * read-before-write, no collision loop, and no coordination on the hot path once a block is
 * held. Each instance claims a block of ids and hands them out locally; only block
 * exhaustion needs the shared counter.
 *
 * <p>The in-memory counter here is a placeholder. Backing it with a Postgres sequence is
 * what makes it survive more than one replica.
 */
@Component
public class ShortCodeGenerator {

  private static final String ALPHABET =
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private final AtomicLong counter = new AtomicLong(1_000_000L);

  public String next() {
    return encodeBase62(counter.getAndIncrement());
  }

  static String encodeBase62(long value) {
    if (value == 0) {
      return "0";
    }
    StringBuilder sb = new StringBuilder();
    long v = value;
    while (v > 0) {
      sb.append(ALPHABET.charAt((int) (v % 62)));
      v /= 62;
    }
    return sb.reverse().toString();
  }
}
