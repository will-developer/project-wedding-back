CREATE TABLE gift (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES event(id),
    name varchar(160) NOT NULL,
    description text,
    image_url varchar(2048),
    price numeric(10, 2) NOT NULL CHECK (price > 0),
    total_quantity integer NOT NULL CHECK (total_quantity > 0),
    available_quantity integer NOT NULL CHECK (available_quantity >= 0),
    active boolean NOT NULL,
    CHECK (available_quantity <= total_quantity)
);
