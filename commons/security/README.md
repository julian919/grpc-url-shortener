# commons-security

Inbound gRPC security. Add the dependency and a service becomes a resource server: every call has
its JWT verified, and each RPC's required permission is read off the proto contract itself.

Three pieces, and they form a chain:

1. `GrpcSecurityAutoConfiguration` — the **wiring**. One `@GlobalServerInterceptor` in front of
   every RPC; `oauth2ResourceServer(jwt)` verifies signature and expiry.
2. `ProtoPermissionAuthorizationManager` — the **policy** it consults. Reads
   `auth.api.requires_permissions` off the method's `MethodOptions` and compares it to the token's
   `permissions` claim. This is what makes the proto option actually enforce anything; without it
   the option is a comment.
3. `AccessTokenExceptionHandler` — the **error mapper**, turning failures into
   `ACCESS_TOKEN_MISSING` / `_EXPIRED` / `_INVALID` with AIP-193 `ErrorInfo` details.

Every bean is `@ConditionalOnMissingBean`, so a service needing different behaviour can declare
its own and win.

Requires, in the consuming service:

```yaml
spring.security.oauth2.resourceserver.jwt:
  public-key-location: classpath:auth-public-key.pem
  authorities-claim-name: permissions
  authority-prefix: ""
```

## Scope

`commons/` holds TECHNICAL concerns only — transport, tracing, logging, security wiring. **No
domain code may be added here**: nothing in this module knows what a link or a principal is.

One focused module per concern, not a catch-all jar. This one depends on `auth-api` (for the
`requires_permissions` extension); a future `commons/tracing` should not inherit that, which is
why it would be a sibling rather than another file in here.

See the root [README](../../README.md) §3 for how this differs from `auth/client`.
