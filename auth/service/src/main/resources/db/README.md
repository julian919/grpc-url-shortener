# auth-service database

Two locations, deliberately separated, selected by profile.

| Location | Runs where | Contains |
|---|---|---|
| `db/migration` | **every** environment (base `spring.flyway.locations`) | schema, and environment-agnostic reference data such as the `roles` catalog |
| `db/dev` | only when the `dev` profile is active (`application-dev.yml`) | `R__init_auth.sql`, the throwaway service clients for local work |

## Why the split

A versioned migration runs everywhere, so anything in `db/migration` is by definition present in
staging and production. That is right for schema and wrong for credentials — and it is a change
that arrives through the ordinary code-merge pipeline, which means a PR approval would be enough to
alter who can authenticate in production. Registering a principal is identity administration, and
it belongs in its own pipeline with its own approvers.

`db/dev` therefore holds the seed, and nothing outside a developer machine can reach it. Opting out
is the default: base config lists `classpath:db/migration` only, so an environment has to opt *in*
by activating the `dev` profile.

## Registering a client in a real environment

Not automated, on purpose. Per environment, once:

1. Generate a secret — `openssl rand -base64 32`. Never reuse one across environments.
2. Store it in that environment's secret manager. It is consumed by the *client* (user-service,
   the webapp), never by auth-service, which only stores the bcrypt hash.
3. Create the principal and its login, hashing in the database so the plaintext never lands in a
   file or shell history. Grant through a **role** from the catalog (`SELECT name, permissions FROM
   roles`) rather than a direct permission -- `principals.permissions` is the escape hatch for a
   genuine one-off, not the normal path:

   ```sql
   -- psql -U auth -d authdb -v client_id=... -v secret=...
   CREATE EXTENSION IF NOT EXISTS pgcrypto;
   WITH p AS (
     INSERT INTO principals (id, type, secret_hash, roles, permissions)
     VALUES (gen_random_uuid(), 'SERVICE',
             crypt(:'secret', gen_salt('bf', 12)),
             ARRAY['WEB_CLIENT'], ARRAY[]::text[])
     RETURNING id
   )
   INSERT INTO logins (id, principal_id, provider, account_id)
   SELECT gen_random_uuid(), id, 'CLIENT_ID', :'client_id' FROM p;
   ```
4. Rotation is the same `crypt(...)` expression in an `UPDATE` on `principals.secret_hash`,
   followed by updating the client's stored secret.

### Choosing the role

- A **backend service** gets its own role, named after the workload: `USER_SERVICE`, and one per
  service added later. A shared "any internal service" role would hand every service the union of
  everyone's permissions -- `CREATE_PRINCIPAL` to a service with no business creating principals.
  Per-workload roles keep each grant minimal and independent.
- A **browser frontend** takes the shared `WEB_CLIENT` role. Frontends are interchangeable in what
  they may read, so a second one reuses it rather than minting a near-duplicate.

The better long-term answer is an admin RPC that **generates** the secret server-side and returns
it once, so no operator ever chooses or handles it (the model Ory Hydra and GitHub use). That is
tracked as an exercise, not shipped.

## Known caveats

- `V3__seed_user_service_client.sql` shipped a bcrypt hash in this repository. It cannot be edited
  (Flyway checksums applied migrations), so `V5__drop_seeded_client_credential.sql` deletes the row
  it created. Treat that credential as public.
- Flyway validates on migrate. A database seeded by `db/dev` and later started *without* the `dev`
  profile fails with `applied migration not resolved locally`. That is intended — a dev database is
  not a production database — so re-create the volume rather than re-pointing it.
