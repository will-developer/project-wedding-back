CREATE TABLE guest_login_attempt (
    source_address varchar(64) PRIMARY KEY,
    window_started_at timestamptz NOT NULL,
    attempts integer NOT NULL CHECK (attempts > 0),
    version bigint NOT NULL
);
