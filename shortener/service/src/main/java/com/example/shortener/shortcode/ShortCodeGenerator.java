package com.example.shortener.shortcode;

import java.util.Base64;
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
 * small, with zero coordination required.
 *
 * <p>The first fix just called {@code uuid.toString()}: 36 characters, hex-encoded at 4 bits
 * per character (32 hex digits + 4 hyphens for 128 bits). This version encodes the exact same
 * 128 bits differently -- split across the UUID's two 64-bit halves, packed byte-by-byte into
 * a 16-byte array by hand, then Base64url-encoded at 6 bits per character -- for 22 characters,
 * no padding. Same random bits, same collision resistance, denser encoding.
 */
@Component
public class ShortCodeGenerator {

  public String next() {
    UUID uuid = UUID.randomUUID();
    long msb = uuid.getMostSignificantBits();
    long lsb = uuid.getLeastSignificantBits();

    byte[] bytes = new byte[16];
    for (int i = 0; i < 8; i++) {
      // Most-significant byte first. Shifting right by 56, 48, ..., 0 bits brings each
      // byte of the long down to the low 8 bits in turn; the (byte) cast truncates to
      // exactly those 8 bits, discarding the rest.
      bytes[i] = (byte) (msb >>> (8 * (7 - i)));
      bytes[i + 8] = (byte) (lsb >>> (8 * (7 - i)));
    }

    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
