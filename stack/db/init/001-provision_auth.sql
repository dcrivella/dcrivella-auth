-- SQL scripts

-- Create a dedicated auth-server app role (with limited privileges)
CREATE ROLE auth_user LOGIN PASSWORD 'auth_pass' NOSUPERUSER NOCREATEDB NOCREATEROLE;
-- Create the application database, owned by the new role
CREATE DATABASE auth_server OWNER auth_user;
-- Ensure the database is owned by the app role
ALTER DATABASE auth_server OWNER TO auth_user;
-- Inside the auth_server database, create a dedicated schema
\connect auth_server
CREATE SCHEMA IF NOT EXISTS auth AUTHORIZATION auth_user;
-- Lock down the public schema so no one can create objects there accidentally
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
-- Make auth_user’s default search_path (so tables are created in "auth")
ALTER ROLE auth_user SET search_path = auth, public;
