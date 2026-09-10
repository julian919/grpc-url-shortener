-- Existing principals are all end users; default them to USER so the column can be NOT NULL.
ALTER TABLE principals ADD COLUMN type varchar(16) NOT NULL DEFAULT 'USER';

INSERT INTO roles (name, permissions) VALUES ('SERVICE_INTERNAL', ARRAY['CREATE_PRINCIPAL']);

-- One SERVICE-typed principal representing user-service itself. Secret is bcrypt('user-service-secret'),
-- cost factor 12 -- same as PrincipalService's own encoder. A real deployment would rotate this via
-- an env-var-driven migration parameter or a secrets manager instead of a checked-in dev hash.
INSERT INTO principals (id, type, secret_hash, roles, permissions)
VALUES (
  gen_random_uuid(),
  'SERVICE',
  '$2a$12$kNJnrAToZ0Vev8j.o/yWnuAG4fMBjaLQi8ZbTSKzi51.s7giKHYeG',
  ARRAY['SERVICE_INTERNAL'],
  ARRAY[]::text[]
);

INSERT INTO logins (id, principal_id, provider, account_id)
SELECT gen_random_uuid(), id, 'CLIENT_ID', 'user-service'
FROM principals
WHERE type = 'SERVICE' AND secret_hash = '$2a$12$kNJnrAToZ0Vev8j.o/yWnuAG4fMBjaLQi8ZbTSKzi51.s7giKHYeG';
