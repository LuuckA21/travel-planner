CREATE TABLE trip_document (
    id                uuid PRIMARY KEY,
    trip_id           uuid        NOT NULL REFERENCES trip (id) ON DELETE CASCADE,
    booking_id        uuid        REFERENCES booking (id) ON DELETE SET NULL,
    original_filename text        NOT NULL,
    content_type      text        NOT NULL,
    size_bytes        bigint      NOT NULL,
    storage_key       text        NOT NULL UNIQUE,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX trip_document_trip_idx ON trip_document (trip_id, created_at);
