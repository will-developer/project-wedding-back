CREATE TABLE admin_account (
  id uuid PRIMARY KEY,
  login varchar(160) NOT NULL UNIQUE,
  password_hash varchar(255) NOT NULL,
  created_at timestamptz NOT NULL,
  status varchar(8) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
  deactivated_at timestamptz,
  CONSTRAINT admin_account_status_date_check CHECK (
    (status = 'ACTIVE' AND deactivated_at IS NULL)
    OR (status = 'INACTIVE' AND deactivated_at IS NOT NULL)
  )
);

-- Every row has the same indexed value, so a second account is rejected.
-- Inactive accounts still occupy the single administrator slot.
CREATE UNIQUE INDEX admin_account_singleton ON admin_account ((true));
