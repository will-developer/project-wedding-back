CREATE TYPE rsvp_status AS ENUM ('pending', 'confirmed', 'declined');

CREATE TABLE event (
  id uuid PRIMARY KEY,
  title varchar(160) NOT NULL,
  event_date timestamptz NOT NULL,
  ceremony_location varchar(200) NOT NULL,
  reception_location varchar(200),
  rsvp_deadline date,
  created_at timestamptz NOT NULL
);

CREATE TABLE invite_group (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event(id),
  group_name varchar(160) NOT NULL,
  invite_code varchar(6) NOT NULL UNIQUE CHECK (invite_code ~ '^[A-Z0-9]{6}$'),
  notes text,
  created_at timestamptz NOT NULL
);

CREATE TABLE guest (
  id uuid PRIMARY KEY,
  group_id uuid NOT NULL REFERENCES invite_group(id),
  name varchar(160) NOT NULL,
  is_child boolean NOT NULL,
  rsvp_status rsvp_status NOT NULL,
  responded_at timestamptz
);
