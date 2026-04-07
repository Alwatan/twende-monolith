-- Matching service schema
-- Minimal database — most state lives in Redis.
-- These tables store audit records that must survive Redis eviction.

-- Audit log of all offers sent to drivers (for analytics and debugging)
CREATE TABLE offer_logs (
    id            UUID         PRIMARY KEY,
    ride_id       UUID         NOT NULL,
    driver_id     UUID         NOT NULL,
    country_code  CHAR(2)      NOT NULL,
    batch_number  INT          NOT NULL,
    distance_km   NUMERIC(6,2),
    score         NUMERIC(5,3),
    offered_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    responded_at  TIMESTAMPTZ,
    response      VARCHAR(20)  -- ACCEPTED, REJECTED, TIMEOUT, RIDE_TAKEN
);

CREATE INDEX idx_offer_logs_ride ON offer_logs(ride_id);
CREATE INDEX idx_offer_logs_driver ON offer_logs(driver_id);
CREATE INDEX idx_offer_logs_offered ON offer_logs(offered_at);

-- Persistent driver stats snapshot (synced from Redis daily)
CREATE TABLE driver_stats_snapshot (
    driver_id       UUID         PRIMARY KEY,
    offered_count   INT          NOT NULL DEFAULT 0,
    accepted_count  INT          NOT NULL DEFAULT 0,
    rejection_count INT          NOT NULL DEFAULT 0,
    acceptance_rate NUMERIC(5,3) NOT NULL DEFAULT 0.0,
    snapshot_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
