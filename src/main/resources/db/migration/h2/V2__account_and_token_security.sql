-- Old refresh and email-change tokens intentionally require a fresh login/request.
ALTER TABLE users ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE admins ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE verification_tokens ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE two_factor_credentials ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE refresh_tokens ADD COLUMN issued_auth_version BIGINT;
ALTER TABLE verification_tokens ADD COLUMN issued_auth_version BIGINT;
ALTER TABLE verification_tokens ADD COLUMN requested_email VARCHAR(255);
ALTER TABLE two_factor_credentials ADD COLUMN challenge_auth_version BIGINT;
ALTER TABLE admin_activity_log ALTER COLUMN details TEXT;
ALTER TABLE user_activity_log ALTER COLUMN details TEXT;
