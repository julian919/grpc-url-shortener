package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shortener.api.ShortLink;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The artefact for lesson 1.
 *
 * <p>These tests do not assert a rule from the docs. They reproduce the actual wire behaviour
 * on raw bytes, which is the thing worth being able to pull up on a laptop when someone asks
 * "what do you mean, protobuf is safe to evolve?"
 *
 * <p>Bytes are hand-built with CodedOutputStream so that a schema which does not exist in this
 * repo -- an older or a deliberately-broken one -- can still be simulated exactly.
 */
class WireCompatibilityTest {

  /** tag = (field_number << 3) | wire_type. Wire type 2 is length-delimited (string). */
  private static byte[] stringField(int fieldNumber, String value) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CodedOutputStream cos = CodedOutputStream.newInstance(out);
    cos.writeString(fieldNumber, value);
    cos.flush();
    return out.toByteArray();
  }

  @Test
  @DisplayName("the tag byte is the field's only identity on the wire")
  void tagByteIsTheIdentity() throws IOException {
    byte[] atFieldTwo = stringField(2, "abc");

    // (2 << 3) | 2 == 0x12, then length 3, then the UTF-8 bytes. The name "long_url"
    // appears nowhere.
    assertThat(atFieldTwo).startsWith((byte) 0x12, (byte) 0x03);
    assertThat(new String(atFieldTwo, 2, 3)).isEqualTo("abc");
  }

  @Test
  @DisplayName("renumbering a field silently loses data -- it does not throw")
  void renumberingIsBreaking() throws IOException {
    // Simulate a WRITER built from a schema where long_url had been moved to field 4.
    byte[] writtenByRenumberedSchema = stringField(4, "https://example.com");

    // Our current schema has long_url = 2. Parsing succeeds -- that is the dangerous part.
    ShortLink parsed = ShortLink.parseFrom(writtenByRenumberedSchema);

    assertThat(parsed.getLongUrl()).isEmpty();                       // the data is gone
    assertThat(parsed.getUnknownFields().asMap()).containsKey(4);    // and it is sitting here

    // No exception was thrown. Every redirect would 404 with a green build.
  }

  @Test
  @DisplayName("unknown fields survive a round-trip through an older reader")
  void unknownFieldsArePreserved() throws IOException {
    // A NEWER writer sets long_url (2) plus some field 8 this build has never heard of.
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(stringField(2, "https://example.com"));
    out.write(stringField(8, "set-by-a-newer-build"));
    byte[] fromNewerSchema = out.toByteArray();

    // This build parses what it knows...
    ShortLink parsed = ShortLink.parseFrom(fromNewerSchema);
    assertThat(parsed.getLongUrl()).isEqualTo("https://example.com");
    assertThat(parsed.getUnknownFields().asMap()).containsKey(8);

    // ...and re-emits field 8 untouched. This is why additive changes are safe, and why an
    // old service in the middle of a call chain does not destroy data it cannot interpret.
    assertThat(parsed.toByteArray()).isEqualTo(fromNewerSchema);
  }

  @Test
  @DisplayName("int32 -> string is breaking because the wire type changes")
  void wireTypeChangeIsBreaking() throws IOException {
    // An old writer wrote created_at (field 3) as a varint, wire type 0.
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CodedOutputStream cos = CodedOutputStream.newInstance(out);
    cos.writeInt64(3, 1_725_600_000_000L);
    cos.flush();
    byte[] asVarint = out.toByteArray();

    // (3 << 3) | 0 == 0x18. Had created_at been declared a string, the tag would be
    // (3 << 3) | 2 == 0x1A and the parser would read a length where a value lives.
    assertThat(asVarint[0]).isEqualTo((byte) 0x18);

    ShortLink parsed = ShortLink.parseFrom(asVarint);
    assertThat(parsed.getCreatedAt()).isEqualTo(1_725_600_000_000L);
  }
}
