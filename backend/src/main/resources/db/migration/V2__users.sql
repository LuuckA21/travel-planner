CREATE TABLE user_account (
    id                uuid PRIMARY KEY,
    email             text        NOT NULL UNIQUE,
    password_hash     text        NOT NULL,
    display_name      text        NOT NULL,
    email_verified_at timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE auth_token (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    purpose    text        NOT NULL,
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX auth_token_user_id_idx ON auth_token (user_id);
