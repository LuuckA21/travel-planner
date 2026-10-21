CREATE TABLE checklist (
    id         uuid PRIMARY KEY,
    trip_id    uuid        NOT NULL REFERENCES trip (id) ON DELETE CASCADE,
    name       text        NOT NULL,
    position   int         NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX checklist_trip_position_idx ON checklist (trip_id, position);

CREATE TABLE checklist_item (
    id           uuid PRIMARY KEY,
    checklist_id uuid        NOT NULL REFERENCES checklist (id) ON DELETE CASCADE,
    text         text        NOT NULL,
    done         boolean     NOT NULL DEFAULT false,
    position     int         NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX checklist_item_checklist_position_idx ON checklist_item (checklist_id, position);
