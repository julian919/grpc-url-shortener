# auth-client

Client code for accessing auth-service from other microservices.

Add the dependency and you get, by auto-configuration:

- a `plain` `AuthServiceBlockingStub` — unintercepted, used ONLY to fetch a token (going through
  the authenticated stub would be circular: a token is needed to get a token)
- a `TokenSupplier` that fetches and caches the client-credentials token, refreshing 30s early

Your service then decorates its OWN outbound stubs:

```java
UserServiceGrpc.newBlockingStub(channels.createChannel("user"))
    .withInterceptors(new BearerTokenAuthenticationInterceptor(serviceTokenSupplier));
```

Requires, in the consuming service:

```yaml
spring.grpc.client.channel.auth.target: "static://auth-service:9092"
app.auth-client.client-id: <this service's client id>
app.auth-client.client-secret: ${SOME_SERVICE_AUTH_SECRET}   # no default, on purpose
```

and a matching client principal registered in auth-service — see
`auth/service/src/main/resources/db/README.md`.

## This is the OUTBOUND half

`auth/client` answers "how do I prove who I am when I call someone?".
`commons/security` answers "how do I check a token someone sent me?".

A service can need one, both, or neither. The full comparison, and why one lives in the auth
domain while the other lives under `commons/`, is in the root [README](../../README.md)
under §3, "`auth/client` vs `commons/security`".
