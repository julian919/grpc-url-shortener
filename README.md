# gRPC Scaling Workshop — code

Two Spring Boot services sharing one protobuf schema, built to demonstrate two things:

1. **A schema no one can quietly break** — one `.proto`, two independently deployable
   services, machine-enforced compatibility.
2. **Reads that scale independently of writes** — `ResolveShortLink` run at N replicas,
   with real load numbers rather than a claim.

---

## 1. The contract guard

The schema is the contract between the two services. This test proves what that buys you,
directly on the wire — no tooling to install, runs in under a second:

```bash
./mvnw -pl shortener/service test -Dtest=WireCompatibilityTest
```

| Test | Demonstrates |
|---|---|
| `tagByteIsTheIdentity` | the field name never travels — only `(number << 3) \| wire_type` |
| `renumberingIsBreaking` | renumbering **silently loses data and does not throw** |
| `unknownFieldsArePreserved` | an old reader round-trips a newer field untouched |
| `wireTypeChangeIsBreaking` | `int32` → `string` desynchronises the parse |

The second one is the one to show someone. It parses bytes written under a schema where
`long_url` sat at field 4, using the current schema where it sits at field 2:

```java
ShortLink parsed = ShortLink.parseFrom(writtenByRenumberedSchema);

assertThat(parsed.getLongUrl()).isEmpty();                    // the data is gone
assertThat(parsed.getUnknownFields().asMap()).containsKey(4); // and it is sitting here
```

No exception is thrown. Every redirect would 404 with a green build. That is the argument
for machine-checking schema changes, made concrete rather than asserted.

**In an interview:** *"A test that reproduces the failure mode on the wire. In a team repo
I'd add `buf breaking --against` as a PR gate on top of it."*

## 2. Build and run

Requires **JDK 21+**. Maven is not needed — `./mvnw` (the Maven Wrapper) downloads and
caches it on first run.

```bash
./mvnw install              # builds all three modules
```

Then, in two terminals — **audit first**, since the shortener opens a channel to it:

```bash
./mvnw -pl linkaudit/service spring-boot:run     # port 9091
./mvnw -pl shortener/service spring-boot:run     # port 9090
```

Both services register `grpc.reflection.v1.ServerReflection` automatically, so `grpcurl`
can introspect them with no `-proto` flag (`brew install grpcurl`):

```bash
grpcurl -plaintext -d '{"long_url":"https://anthropic.com"}' \
  localhost:9090 shortener.api.v1.ShortenerService/CreateShortLink

grpcurl -plaintext -d '{"long_url":"https://malware.test/x"}' \
  localhost:9090 shortener.api.v1.ShortenerService/CreateShortLink
# -> status comes back LINK_STATUS_FLAGGED, decided by the OTHER service,
#    using the SAME generated enum
```

---

## 3. Layout

```
shortener/
  api/shortener_api.proto         package shortener.api
  service/                        Spring Boot app, gRPC on 9090
linkaudit/
  api/link_audit_api.proto        package linkaudit.api
  service/                        Spring Boot app, gRPC on 9091
```

Laid out by **domain**, the way the Cognixus monorepo is: each domain owns an `api/`
directory holding its schema and a `service/` directory holding the application.

### Protos are addressed from the repo root

```proto
// linkaudit/api/v1/link_audit_api.proto
import "shortener/api/shortener_api.proto";
```

```proto
// cognixus/cms/api/cms_api.proto  -- the same shape
import "auth/api/auth_api.proto";
import "commons/api/commons_api.proto";
```

Bazel gives Cognixus this for free. In Maven it takes two settings on each `api` module:

| Setting | Effect |
|---|---|
| `<sourceDirectories>` = `${project.basedir}` | compile **only this domain's** protos |
| `<importPaths>` = repo root | make **everyone's** protos resolvable, uncompiled |
| `<ignoreProjectDependencies>true` | don't *also* unpack protos from dependency jars |
| `<excludes>` = `target/**` | don't rescan the copy the plugin puts in `target/classes` |

Neither of the last two is optional, and both fail confusingly:

- without `ignoreProjectDependencies`, the plugin finds `shortener_api.proto` twice — once
  on the filesystem, once unpacked from the `shortener-api` jar — and aborts on `Duplicate key`
- without the `target/**` exclude, `embedSourcesInClassOutputs` (on by default) copies the
  proto into `target/classes` so it ships in the jar, and the next build compiles that copy
  as a second, conflicting definition

The filesystem root is the single source of truth, as in Bazel.

**Verified:** `linkaudit/api/target` contains only its own 6 generated files.
`ShortenerServiceGrpc.java` is absent — the import resolved without regenerating.

### Why the schema is not inside the service directory

`shortener/api` and `shortener/service` are separate Maven modules even though they share a
parent folder. If the schema lived inside the service module, link-audit would have to
depend on the **entire shortener application** — Netty, Spring context, future datasource —
to read one enum. Bazel splits these the same way: `cms/api/BUILD` and `cms/service/BUILD`
are distinct targets under one directory.

### The two arrows point in opposite directions

```
schema:    linkaudit/api      ──imports──▶  shortener/api
runtime:   shortener/service  ──calls────▶  linkaudit/service
```

Not a mistake, and worth rehearsing: a **schema** dependency is about shared vocabulary,
a **runtime** dependency is about who calls whom.

### Package matches path

`shortener/api/shortener_api.proto` declares `package shortener.api` — path and package line
up segment for segment, exactly as Cognixus does it (`cms/api` → `cms.api`).

**No version segment, deliberately.** Versioning the package (`shortener.api.v1`) is a
Buf/Google-API house rule — `buf lint`'s `PACKAGE_VERSION_SUFFIX`, in the `STANDARD` set —
and the [official protobuf style guide](https://protobuf.dev/programming-guides/style/)
never mentions it. Worth knowing that Cognixus's own `package cms.api` would fail that rule.

When a genuinely breaking change is needed, the move is to publish a parallel
`shortener.api.v2` package and migrate callers across, rather than editing in place. If buf
is ever switched on (§5), relax `STANDARD` to `BASIC` or exclude that one rule.

Look at the imports at the top of `LinkAuditGrpcService.java`:

```java
import com.example.linkaudit.api.v1.ReportLinkCreatedRequest;  // its own types
import com.example.shortener.api.v1.LinkStatus;                // someone else's enum
```

---

## 3b. Two kinds of failure, two different mechanisms

`CreateShortLink` and `ResolveShortLink` fail in genuinely different ways, and the code
treats them differently on purpose — mirroring a real pattern from Cognixus's
`error/api/error_api.proto`.

**`ResolveShortLink` uses a real gRPC status** (`NOT_FOUND`). "This resource doesn't exist"
is a transport-level outcome — exactly what gRPC's status codes exist for. It ends the call:
`onError(...)`, no response message.

**`CreateShortLink` uses a `oneof` in the response body** for a blank `long_url`:

```proto
message CreateShortLinkResponse {
  oneof response {
    ShortLink link  = 1;
    Error     error = 2;
  }
}
```

This is a *business* failure, not a transport one — the RPC succeeded, the server understood
the request fine, a validation rule said no. That doesn't belong in a Status trailer (which
is exactly the layer Postman rendered so unhelpfully — see Lesson 4). It belongs in the
response, as ordinary structured data: `onNext(...)` with the `error` branch set, then
`onCompleted()`. The call **succeeded**; the caller checks `getResponseCase()`.

**The distinction worth getting right**: a link flagged by link-audit (`status =
LINK_STATUS_FLAGGED`) is *not* routed through `Error`. Being flagged is a legitimate
business **state** of a successfully created link — it still comes back on the `link`
branch. `Error` is reserved for "this operation could not be carried out at all," not for
"the operation succeeded with a state you should pay attention to." Conflating the two would
make every flagged link indistinguishable from a rejected one.

## 4. Version matrix (read the BOMs, not the blog posts)

Every version below was read out of a published POM, not guessed. This matters more than
usual right now:

| Component | Version | Where it comes from |
|---|---|---|
| Spring Boot | 4.1.1 | latest stable (4.2.0-M1 is a milestone) |
| Spring gRPC | 1.1.1 | `spring-grpc.version` in `spring-boot-dependencies:4.1.1` |
| grpc-java | 1.83.1 | `grpc-java.version` → `grpc-bom`, same POM |
| protobuf-java / protoc | 4.35.1 | `protobuf-java.version` in `spring-grpc-dependencies:1.1.1` |
| protobuf-maven-plugin | 5.1.8 | `io.github.ascopes`, Maven Central |

**The trap:** as of Spring gRPC 1.1 the starters moved *into Spring Boot*:

```xml
<!-- correct, current -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-grpc-server</artifactId>
</dependency>

<!-- what most blog posts and most generated code still show -->
<dependency>
  <groupId>net.devh</groupId>
  <artifactId>grpc-server-spring-boot-starter</artifactId>
</dependency>
```

Config keys moved too: `spring.grpc.client.channels.<n>.address` →
`spring.grpc.client.channel.<n>.target`.

---

## 5. Future enhancement: machine-enforced schema checks

Deliberately **not** set up yet — it needs a `buf` install, and the JUnit test above already
carries the idea. Reach for this when the schema is shared with someone who is not you.

[Buf](https://buf.build/docs/breaking/) compares the working tree's schema against a git ref
and fails on any wire-incompatible edit.

```yaml
# buf.yaml
version: v2
modules:
  # One module at the repo root, so a proto's path IS its import string.
  - path: .
    excludes:
      - shortener/service
      - linkaudit/service
lint:
  use: [STANDARD]
breaking:
  use: [FILE]      # Buf's recommended default
```

```sh
# hooks/pre-commit   — activate with: git config core.hooksPath hooks
#!/bin/sh
set -e
buf lint
buf breaking --against '.git#branch=main'
```

```bash
brew install bufbuild/buf/buf
git config core.hooksPath hooks
```

Then renumber a field and watch the commit get refused.

**One thing to know before enabling it:** `STANDARD` lint includes `PACKAGE_DIRECTORY_MATCH`,
which requires a file's path to match its package. The layout already satisfies this —
`shortener/api/v1/shortener_api.proto` declares `package shortener.api.v1` — so it should
pass clean on day one. That constraint is *why* the `v1` directory segment exists; it is
good practice regardless, just not currently enforced by anything.

## 6. Status

| Piece | State |
|---|---|
| Schema modules, Cognixus-style layout | ✅ repo-root imports verified |
| Both services, cross-service call | ✅ **verified end-to-end**, in-memory store |
| Pre-commit `buf` guard | ⬜ deferred, see §5 |
| Wire-compatibility tests | ✅ 4/4 passing, no tooling needed |
| Postgres + Redis cache-aside | ⬜ later lesson |
| Docker Compose, N replicas | ⬜ later lesson |
| L4-vs-L7 load balancing demo | ⬜ later lesson |
| ghz benchmark numbers | ⬜ later lesson |

### Verified 2026-09-06

`./mvnw install` succeeds, all four wire-compatibility tests pass, and both services were
run together and exercised end-to-end:

```
clean   -> code=4c92 status=LINK_STATUS_ACTIVE
blocked -> code=4c93 status=LINK_STATUS_FLAGGED
resolve -> https://anthropic.com
missing -> NOT_FOUND
```

The `LINK_STATUS_FLAGGED` on line two was decided by **link-audit-service** and carried
back to **shortener-service** as the shared enum. Two processes, one definition.
