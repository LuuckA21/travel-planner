CREATE TABLE trip (
    id                uuid PRIMARY KEY,
    owner_id          uuid        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    name              text        NOT NULL,
    start_date        date        NOT NULL,
    end_date          date        NOT NULL,
    cover_storage_key text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX trip_owner_id_idx ON trip (owner_id);

CREATE TABLE trip_destination (
    trip_id  uuid NOT NULL REFERENCES trip (id) ON DELETE CASCADE,
    position int  NOT NULL,
    name     text NOT NULL,
    PRIMARY KEY (trip_id, position)
);
