ALTER TABLE event
  ALTER COLUMN reception_location SET NOT NULL,
  ALTER COLUMN rsvp_deadline SET NOT NULL,
  ADD COLUMN ceremony_maps_url varchar(2048) NOT NULL,
  ADD COLUMN reception_maps_url varchar(2048) NOT NULL;
