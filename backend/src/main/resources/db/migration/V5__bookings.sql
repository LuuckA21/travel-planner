CREATE TABLE booking (
    id         uuid PRIMARY KEY,
    trip_id    uuid        NOT NULL REFERENCES trip (id) ON DELETE CASCADE,
    type       text        NOT NULL,
    title      text        NOT NULL,
    provider   text,
    reference  text,
    start_at   timestamp   NOT NULL,
    end_at     timestamp,
    origin     text,
    location   text,
    notes      text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX booking_trip_start_idx ON booking (trip_id, start_at);
