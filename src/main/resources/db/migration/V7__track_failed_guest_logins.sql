ALTER TABLE guest_login_attempt ADD COLUMN failed_attempts integer;

UPDATE guest_login_attempt SET failed_attempts = 0;

ALTER TABLE guest_login_attempt
    ALTER COLUMN failed_attempts SET NOT NULL,
    ADD CONSTRAINT guest_login_attempt_failed_attempts_check CHECK (failed_attempts >= 0);
