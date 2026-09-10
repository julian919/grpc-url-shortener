package com.example.shortener.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Level 1: a pure function test. No mocks, no Spring, no gRPC -- just call the method and
 * check what comes back. This is the simplest form a unit test takes, and it's worth being
 * completely comfortable with it before adding any complexity.
 */
class ShortCodeGeneratorTest {

  @Test
  void nextReturnsANonEmptyCode() {
    ShortCodeGenerator generator = new ShortCodeGenerator();

    String code = generator.next();

    assertThat(code).isNotBlank();
  }

  @Test
  void nextReturnsA22CharacterUrlSafeCode() {
    // 128 bits at 6 bits/char (Base64url) is 22 characters unpadded -- a specific,
    // checkable number, not just "some string".
    ShortCodeGenerator generator = new ShortCodeGenerator();

    String code = generator.next();

    assertThat(code).hasSize(22);
    assertThat(code).matches("[A-Za-z0-9_-]+"); // URL-safe alphabet; no '+', '/', or '=' padding
  }

  @Test
  void consecutiveCallsReturnDifferentCodes() {
    ShortCodeGenerator generator = new ShortCodeGenerator();

    String first = generator.next();
    String second = generator.next();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void separateInstancesDoNotCollide() {
    // The exact property the old counter-based generator got wrong: two DIFFERENT
    // instances (standing in for two different replicas, each with no knowledge of the
    // other) must not produce the same code. Confirmed live against three real replicas
    // before this change -- they all produced "4c92" as their first code. This test is
    // the regression guard for that specific bug, not a generic randomness smoke test.
    ShortCodeGenerator replicaA = new ShortCodeGenerator();
    ShortCodeGenerator replicaB = new ShortCodeGenerator();
    ShortCodeGenerator replicaC = new ShortCodeGenerator();

    String fromA = replicaA.next();
    String fromB = replicaB.next();
    String fromC = replicaC.next();

    assertThat(Set.of(fromA, fromB, fromC)).hasSize(3);
  }

  @Test
  void manyCallsProduceNoDuplicates() {
    ShortCodeGenerator generator = new ShortCodeGenerator();

    Set<String> codes = new HashSet<>();
    IntStream.range(0, 10_000).forEach(i -> codes.add(generator.next()));

    assertThat(codes).hasSize(10_000);
  }
}
