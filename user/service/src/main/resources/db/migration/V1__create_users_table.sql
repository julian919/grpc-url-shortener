-- The profile user-service owns.
--
-- `id` is this service's own key. `principal_id` is a reference to the identity auth-service
-- minted -- and it is NOT a foreign key, because it cannot be: principals live in authdb, a
-- different database owned by a different service. Referential integrity stops at the service
-- boundary, so the UNIQUE constraint below is what actually enforces one profile per identity.
--
-- Why not make principal_id the primary key, since the mapping is 1:1? Because it would make this
-- table's identity depend on another service's id scheme. Swap auth-service for Keycloak, or let a
-- user exist before an identity does (an invite flow), and a borrowed primary key has to be
-- rewritten everywhere it was copied. A surrogate key costs one column and insulates us from that.
-- Same shape as Cognixus's BaseUser: its own _id, principalId as an ordinary indexed field.
--
-- No email and no password here. Those are credentials; they live in auth-service and are never
-- mirrored, so there is nothing to drift and nothing extra to leak.
CREATE TABLE users (
  id           uuid PRIMARY KEY,
  principal_id uuid         NOT NULL UNIQUE,
  first_name   VARCHAR(128) NOT NULL,
  last_name    VARCHAR(128) NOT NULL,
  status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at   TIMESTAMPTZ  NOT NULL,
  updated_at   TIMESTAMPTZ  NOT NULL
);

-- Lookups arrive as a principal id: it is what a JWT's `sub` carries, so it is how every caller
-- outside this service names a user. The UNIQUE constraint above already indexes it.
CREATE INDEX idx_users_status ON users (status);
