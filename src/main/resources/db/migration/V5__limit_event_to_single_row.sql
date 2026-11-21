-- Enforce one wedding for this installation, including concurrent inserts.
CREATE UNIQUE INDEX event_singleton ON event ((true));
