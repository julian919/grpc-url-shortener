# gRPC Scaling Workshop — code

Two Spring Boot services sharing one protobuf schema, built to demonstrate two things:

1. **A schema no one can quietly break** — one `.proto`, two independently deployable
   services, machine-enforced compatibility.
2. **Reads that scale independently of writes** — `GetShortLink` run at N replicas,
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

Requires **JDK 21+** for the services and **Node 24+** for `webapp`. Maven is not needed —
`./mvnw` (the Maven Wrapper) downloads and caches it on first run.

```bash
./mvnw install -DskipTests        # builds every module
docker compose up -d              # or bring the whole stack up
```

Or run one service directly:

```bash
./mvnw -pl shortener/service spring-boot:run     # port 9090
```

### Regenerating code from the protos

There is no separate codegen step for Java: the `protobuf-maven-plugin` is bound to Maven's
`generate-sources` phase, so **any ordinary build regenerates**. TypeScript is a separate
command, because `webapp` has its own toolchain (buf + protobuf-es).

| Target | Command | Output |
|---|---|---|
| Java | `./mvnw clean install -DskipTests` | `<domain>/api/target/generated-sources/protobuf/` |
| TypeScript | `cd webapp && npm run gen:proto` | `webapp/src/shared/api/gen/` |

After changing a `.proto`, do all three:

```bash
./mvnw clean install -DskipTests \
  && (cd webapp && npm run gen:proto && npm run check) \
  && docker compose up -d --build
```

Three things worth knowing, each of which has bitten this repo:

- **`clean` is required when you rename or delete** a message or rpc. Without it, the
  previous run's generated `.java` files are still sitting in `target/generated-sources`,
  javac compiles them too, and you get a wall of `cannot find symbol` errors that look like
  a source problem but are stale output. Pure additions — a new field, a new rpc — are fine
  without it. `npm run gen:proto` has no such issue: `buf.gen.yaml` sets `clean: true`, so
  buf wipes its own output directory first.
- **The Envoy descriptor is generated from the protos too** (`edge/descriptor`), so a new
  `google.api.http` route does not exist at the edge until that image is rebuilt. A restart
  is not enough — `docker compose up -d --build` is.
- **Well-known `google/*` protos are vendored**, not taken from protoc's bundled copies. If
  an import fails with `File not found`, the fix is to add the file under `google/` — it can
  be extracted from the protobuf jar, which is how `google/protobuf/field_mask.proto` got
  here.

### Poking the gRPC surface directly

Each service registers `grpc.reflection.v1.ServerReflection`, so `grpcurl` can introspect it
with no `-proto` flag (`brew install grpcurl`):

```bash
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext -d '{"short_code":"abc123"}' \
  localhost:9090 shortener.api.ShortenerService/GetShortLink
```

`GetShortLink` is public. `CreateShortLink`, `ListShortLinks` and `UpdateShortLink` are gated
on permissions declared in the proto itself, so those need a token:

```bash
grpcurl -plaintext -H "authorization: Bearer $TOKEN" \
  -d '{"long_url":"https://anthropic.com"}' \
  localhost:9090 shortener.api.ShortenerService/CreateShortLink
```

A blocked host (`https://malware.test`) comes back as `FAILED_PRECONDITION`, not as a link
with `LINK_STATUS_FLAGGED` — being refused is an error, not a state a stored link can hold.

---

## 3. Layout

```
shortener/
  api/shortener_api.proto         package shortener.api
  service/                        Spring Boot app, gRPC on 9090
auth/
  api/auth_api.proto              package auth.api
  client/                         how OTHER services authenticate TO auth
  service/                        Spring Boot app, gRPC on 9092
user/
  api/user_api.proto              package user.api
  service/                        Spring Boot app, gRPC on 9093

commons/                          not a domain -- shared TECHNICAL concerns
  security/                       inbound token verification + permission enforcement
edge/
  descriptor/                     the combined proto descriptor Envoy transcodes from
```

Laid out by **domain**, the way the Cognixus monorepo is: each domain owns an `api/`
directory holding its schema and a `service/` directory holding the application.

### Protos are addressed from the repo root

```proto
// shortener/api/shortener_api.proto
import "auth/api/auth_api.proto";
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

**Verified:** `shortener/api/target` contains only its own generated files.
`AuthServiceGrpc.java` is absent — the import resolved without regenerating.

### Why the schema is not inside the service directory

`shortener/api` and `shortener/service` are separate Maven modules even though they share a
parent folder. If the schema lived inside the service module, shortener would have to
depend on the **entire auth application** — Netty, Spring context, its datasource — to read
one custom option. Bazel splits these the same way: `cms/api/BUILD` and `cms/service/BUILD`
are distinct targets under one directory.

### The two arrows point in opposite directions

```
schema:    shortener/api      ──imports──▶  auth/api
runtime:   user/service       ──calls────▶  auth/service
```

Not a mistake, and worth rehearsing: a **schema** dependency is about shared vocabulary,
a **runtime** dependency is about who calls whom.

### `auth/client` vs `commons/security` — the two shared modules

Both are "shared code about auth", which makes them easy to confuse. They differ on two axes.

**Axis 1 — direction.** This is the one that decides what a module contains.

|                 | `auth/client`                          | `commons/security`                          |
| --------------- | -------------------------------------- | ------------------------------------------- |
| **Direction**   | **outbound**                           | **inbound**                                 |
| Question        | "how do I prove who I am when I call?" | "how do I check a token sent to me?"        |
| Contents        | `ServiceTokenSupplier`, `AuthClientProperties`, auto-config | `GrpcSecurityAutoConfiguration`, `ProtoPermissionAuthorizationManager`, `AccessTokenExceptionHandler` |
| Makes you a…    | **client** of auth-service             | **resource server**                         |
| Needs           | `spring-boot-starter-grpc-client`      | `spring-boot-starter-oauth2-resource-server` |

A service can need one, both, or neither:

| Service       | `auth/client` | `commons/security` | Why                                            |
| ------------- | ------------- | ------------------ | ---------------------------------------------- |
| **auth**      | no            | yes                | it *issues* tokens; it never needs to obtain one |
| **user**      | yes           | yes                | verifies `REGISTER`/`GET_USER` inbound, calls auth outbound |
| **shortener** | yes           | yes                | verifies `CREATE_SHORT_URL` etc., calls user-service outbound |

**Axis 2 — who owns it.** This is the one that decides *where the folder lives*.

`auth/client` sits beside `auth/api` and `auth/service` because it belongs to the **auth domain** —
it is auth's published client SDK, "here is how you talk to me", versioned with auth's contract.
Change `GetClientToken` and this is what gets updated. Cognixus has the same module, with a README
saying exactly that.

`commons/security` is **not a domain**. It is a capability every gRPC service needs regardless of
what that service does. The analogy that makes it stick: `auth/client` is like `stripe-java` — a
client for one specific service; `commons/security` is like `spring-boot-starter-security` —
infrastructure.

**Why `commons/<concern>` and not one flat `commons`.** Cognixus's `commons/` is two dozen
independently buildable modules (`server`, `tracing`, `logger`, `kafka`, `storage`…), not one jar.
That matters here: `commons/security` depends on `auth-api`, because
`ProtoPermissionAuthorizationManager` reads `auth.api.requires_permissions` off each method's
options. Flat, a service wanting only tracing would inherit `auth-api` for nothing. As siblings,
each concern carries only its own dependencies — so a tracing interceptor becomes
`commons/tracing`, not another file in this jar.

**The rule that keeps `commons/` from rotting:** technical concerns only — transport, tracing,
logging, security wiring. Nothing that knows what a link or a principal *is*. `commons` is a name
that invites dumping; this rule plus the per-concern split is what stops it.

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

Look at the imports at the top of `ProtoPermissionAuthorizationManager.java`:

```java
import com.example.shortener.api.ShortenerServiceGrpc;  // its own types
import com.example.auth.api.AuthProto;                  // someone else's custom option
```

---

## 3b. Two kinds of failure, two different mechanisms

`CreateShortLink` and `GetShortLink` fail in genuinely different ways, and the code
treats them differently on purpose — mirroring a real pattern from Cognixus's
`error/api/error_api.proto`.

**`GetShortLink` uses a real gRPC status** (`NOT_FOUND`). "This resource doesn't exist"
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

**The distinction worth getting right**: a flagged link (`status =
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
      - auth/service
      - user/service
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

## 5b. Running scaled — client-side round-robin, no proxy

```bash
docker compose up -d --build --scale shortener-service=3
```

No Envoy, no proxy, no extra container. A caller resolves the Compose service name directly
via Docker's embedded DNS and load-balances client-side —
[Lesson 6](../lessons/0006-why-your-load-balancer-lies-to-you.html)'s Option B.

**An Envoy-based version (Option A) was built and verified working first** — see Lesson 6
for the full story of why it was removed. Short version: asked to explain `envoy.yaml`
cold, the honest answer was "not yet" — and a piece of the project that can't be defended
under a follow-up question isn't worth keeping, however impressive. The simpler version
below is what's actually in this repo now.

Because Docker Compose's embedded DNS namespace only exists *inside* its own network, your
host machine can't resolve `shortener-service` directly — run load-test tools as containers
on the same network instead:

```bash
docker run --rm --network code_default ghcr.io/bojand/ghz:latest \
  --insecure \
  --call shortener.api.ShortenerService.CreateShortLink \
  -d '{"long_url":"https://anthropic.com"}' \
  --lb-strategy round_robin \
  -c 20 -n 2000 \
  dns:///shortener-service:9090
```

```bash
docker compose down   # tear it all down
```

## 5c. ghz results (Lesson 7) -- the honest ones, not the expected ones

Full investigation and interpretation: [lessons/0007-proving-it-with-ghz.html](../lessons/0007-proving-it-with-ghz.html).
Short version: scaling 1->3 replicas did NOT improve CreateShortLink throughput
(1702 -> 1508 req/s, p99 roughly 2.4x worse) -- and this is the SECOND time this exact
pattern showed up, first with an Envoy proxy in the path, now with none at all. Testing
both rules the proxy out as the cause: every replica and the ghz
client itself share one laptop's finite CPU cores regardless of architecture -- more
containers isn't more hardware. Separately, GetShortLink for one just-created code
came back roughly 2-in-3 OK through the 3-replica pool (not the naive 1-in-3 a single
independent store predicts, worth digging into further) -- proof reads aren't reliably
consistent under scaling yet, since each replica holds independent in-memory state. That's
the real argument for Postgres next, not just durability.

## 6. Status

| Piece | State |
|---|---|
| Schema modules, Cognixus-style layout | ✅ repo-root imports verified |
| Both services, cross-service call | ✅ **verified end-to-end**, in-memory store |
| Pre-commit `buf` guard | ⬜ deferred, see §5 |
| Wire-compatibility tests | ✅ 4/4 passing, no tooling needed |
| Unit tests (ShortenerGrpcService, ShortCodeGenerator) | ✅ 23/23 passing — see Lesson 7b |
| Postgres + Redis cache-aside | ⬜ later lesson |
| Docker Compose, N replicas | ✅ built and verified 2026-09-07 |
| Client-side round-robin (Option B) | ✅ proven live — see §5b |
| Envoy (Option A) | built, verified, then deliberately removed — see Lesson 6 |
| ghz benchmark numbers | ✅ done — see §5c |

### Verified 2026-09-06

`./mvnw install` succeeds, all four wire-compatibility tests pass, and both services were
run together and exercised end-to-end:

```
clean   -> code=4c92 status=LINK_STATUS_ACTIVE
blocked -> code=4c93 status=LINK_STATUS_FLAGGED
resolve -> https://anthropic.com
missing -> NOT_FOUND
```

The `LINK_STATUS_FLAGGED` on line two is decided by shortener-service's own blocklist
check. It was originally a separate link-audit-service returning the shared enum over
gRPC; the check was inlined once the blocklist no longer justified a network hop.
