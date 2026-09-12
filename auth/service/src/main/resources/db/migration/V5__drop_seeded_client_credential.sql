-- Removes the user-service credential that V3__seed_user_service_client.sql inserted with a
-- bcrypt hash checked into this repository.
--
-- V3 itself cannot be edited -- Flyway checksums applied migrations -- so it stays as history and
-- this migration undoes its data. The hash it carried is public in git forever, which makes that
-- credential compromised by definition in every environment that ever ran V3.
--
-- Targeted by the exact hash, not by account_id: an environment that has already replaced this
-- credential with a real one must keep it. If the hash does not match, this deletes nothing.
--
-- Afterwards:
--   dev  -- db/dev/R__init_auth.sql re-creates the client with a dev secret (repeatable
--           migrations always run after versioned ones, so the ordering is guaranteed).
--   prod -- create the client deliberately, out of band. It is intentionally NOT automated:
--           registering a principal is an identity-administration action and must not ride in on
--           a code merge.
DELETE FROM logins
WHERE provider = 'CLIENT_ID'
  AND account_id = 'user-service'
  AND principal_id IN (
    SELECT id FROM principals
    WHERE type = 'SERVICE'
      AND secret_hash = '$2a$12$kNJnrAToZ0Vev8j.o/yWnuAG4fMBjaLQi8ZbTSKzi51.s7giKHYeG'
  );

DELETE FROM principals
WHERE type = 'SERVICE'
  AND secret_hash = '$2a$12$kNJnrAToZ0Vev8j.o/yWnuAG4fMBjaLQi8ZbTSKzi51.s7giKHYeG'
  AND id NOT IN (SELECT principal_id FROM logins);
