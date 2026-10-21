CREATE TABLE activity (
    id         uuid PRIMARY KEY,
    trip_id    uuid        NOT NULL REFERENCES trip (id) ON DELETE CASCADE,
    date       date        NOT NULL,
    position   int         NOT NULL,
    title      text        NOT NULL,
    start_time time,
    location   text,
    maps_url   text,
    notes      text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX activity_trip_date_position_idx ON activity (trip_id, date, position);
