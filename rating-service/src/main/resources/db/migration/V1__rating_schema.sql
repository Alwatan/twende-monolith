-- V1__rating_schema.sql
-- Rating service schema: mutual rider/driver ratings per completed ride

CREATE TABLE ratings (
    id            UUID        PRIMARY KEY,
    country_code  CHAR(2)     NOT NULL,
    ride_id       UUID        NOT NULL,
    rated_user_id UUID        NOT NULL,
    rater_user_id UUID        NOT NULL,
    rater_role    VARCHAR(10) NOT NULL CHECK (rater_role IN ('RIDER', 'DRIVER')),
    score         SMALLINT    NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(ride_id, rater_role)
);

CREATE INDEX idx_ratings_rated_user ON ratings(rated_user_id);
CREATE INDEX idx_ratings_rater_user ON ratings(rater_user_id);
CREATE INDEX idx_ratings_ride ON ratings(ride_id);
