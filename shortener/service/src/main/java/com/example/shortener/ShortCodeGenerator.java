package com.example.shortener;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A previous version of this class used a per-instance counter with base62 encoding --
 * removed after discovering it had a real bug: every replica starts its counter at the same
 * value with no coordination between instances, so any two replicas that have served the
 * same number of prior calls generate the byte-for-byte identical short code for two
 * different long URLs. Confirmed live: three freshly started replicas all produced
 * {@code "4c92"} as their first code.
 *
 * <p>{@link UUID#randomUUID()} sidesteps the whole problem -- 122 random bits per call means
 * the chance of any two calls, from any number of replicas, ever colliding is astronomically
 * small, with zero coordination required. The trade-off, worth being explicit about: a UUID
 * is 36 characters, considerably longer than the old 4-character codes -- less "short" for a
 * URL shortener, in exchange for correctness across replicas without a shared datastore.
 */
@Component
public class ShortCodeGenerator {

  public String next() {
    return UUID.randomUUID().toString();
  }
}
