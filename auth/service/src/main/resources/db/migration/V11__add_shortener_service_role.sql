-- shortener-service calls user-service's GetUser before accepting a write, to check the author is
-- still ACTIVE. One role per workload, as with USER_SERVICE: a shared "internal service" role
-- would hand every service the union of everyone's permissions.
INSERT INTO roles (name, permissions)
VALUES ('SHORTENER_SERVICE', ARRAY['GET_USER'])
ON CONFLICT (name) DO NOTHING;
