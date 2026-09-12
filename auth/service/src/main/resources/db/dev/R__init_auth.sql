-- DEV SEED -- NEVER RUNS IN PRODUCTION.
--
-- Reachable only when the "dev" profile is active, because that profile is what adds
-- classpath:db/dev to spring.flyway.locations (see application-dev.yml). Base config lists only
-- classpath:db/migration, so an environment that forgets to opt out has still opted out.
--
-- Why here and not in db/migration: a versioned migration runs in EVERY environment, which would
-- put these credentials in staging and prod. Flyway's own guidance is to keep environment-specific
-- data in a separate location outside the migrations folder and select it per environment.
--
-- Why REPEATABLE (R__) rather than versioned (V__): a repeatable migration carries no version, so
-- it can never collide with, or sit out of order against, a real schema migration added later. It
-- also always runs after every versioned migration, which is what this needs -- V5 drops the old
-- checked-in credential, and this re-creates it with a dev value afterwards.
--
-- Caveat worth knowing: Flyway validates on migrate, so a database that ran this seed and is later
-- started WITHOUT the dev profile will fail with "applied migration not resolved locally". That is
-- correct behaviour (a dev database is not a prod database), but it means you re-create the volume
-- rather than re-point it.
--
-- The plaintext secrets below are deliberate, throwaway dev values, and must match the compose
-- defaults in compose.yaml. Hashing happens here via pgcrypto so that no bcrypt hash is committed:
-- a hash is credential material too -- offline-crackable, and permanent once in git history.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Idempotent because a repeatable migration re-runs whenever its checksum changes. ON CONFLICT
-- targets logins(provider, account_id), the unique constraint from V1__baseline.sql.
DO $$
DECLARE
  seed   record;
  new_id uuid;
BEGIN
  FOR seed IN
    SELECT *
    FROM (VALUES
      -- Grants come from ROLES, never direct permissions: the role catalog is the one place a
      -- capability is defined, and db/migration owns it. The last column is the direct-grant
      -- escape hatch and should stay empty here.
      ('user-service', 'user-service-secret',  ARRAY['USER_SERVICE'], ARRAY[]::text[]),
      ('publicweb',    'publicweb-dev-secret', ARRAY['WEB_CLIENT'],        ARRAY[]::text[]),
      ('shortener-service', 'shortener-service-secret', ARRAY['SHORTENER_SERVICE'], ARRAY[]::text[])
    ) AS t(client_id, secret, roles, permissions)
  LOOP
    IF EXISTS (SELECT 1 FROM logins
               WHERE provider = 'CLIENT_ID' AND account_id = seed.client_id) THEN
      CONTINUE;
    END IF;

    new_id := gen_random_uuid();

    INSERT INTO principals (id, type, secret_hash, roles, permissions)
    VALUES (new_id, 'SERVICE', crypt(seed.secret, gen_salt('bf', 12)), seed.roles, seed.permissions);

    INSERT INTO logins (id, principal_id, provider, account_id)
    VALUES (gen_random_uuid(), new_id, 'CLIENT_ID', seed.client_id);

    RAISE NOTICE 'dev seed: created service client %', seed.client_id;
  END LOOP;
END $$;
