-- Precomputed destination visit stats for "frequent destinations" suggestions.
-- Coordinates are rounded to 4 decimal places (~11m) for grouping.

CREATE TABLE user_destination_stats (
    id              UUID         PRIMARY KEY,
    country_code    CHAR(2)      NOT NULL,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    city_id         UUID         NOT NULL,
    destination_lat NUMERIC(10,7) NOT NULL,
    destination_lng NUMERIC(10,7) NOT NULL,
    destination_address TEXT,
    trip_count      INT          NOT NULL DEFAULT 1,
    last_trip_at    TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(user_id, city_id, destination_lat, destination_lng)
);

CREATE INDEX idx_dest_stats_user_city ON user_destination_stats(user_id, city_id, trip_count DESC);
