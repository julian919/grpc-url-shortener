CREATE TABLE principals (
  id          uuid PRIMARY KEY,
  secret_hash varchar(255) NOT NULL,
  roles       text[] NOT NULL DEFAULT '{}',
  permissions text[] NOT NULL DEFAULT '{}',
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE logins (
  id           uuid PRIMARY KEY,
  principal_id uuid NOT NULL REFERENCES principals(id),
  provider     varchar(32) NOT NULL,
  account_id   varchar(255) NOT NULL,
  UNIQUE (provider, account_id)
);

CREATE TABLE roles (
  name        varchar(64) PRIMARY KEY,
  permissions text[] NOT NULL DEFAULT '{}'
);
