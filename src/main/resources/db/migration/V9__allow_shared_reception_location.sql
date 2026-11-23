ALTER TABLE event
    ALTER COLUMN reception_location DROP NOT NULL,
    ALTER COLUMN reception_maps_url DROP NOT NULL;
