-- V1__loyalty_schema.sql
-- Loyalty service schema: rules, rider progress, and free ride offers

CREATE TABLE loyalty_rules (
    id                        UUID          PRIMARY KEY,
    country_code              CHAR(2)       NOT NULL,
    vehicle_type              VARCHAR(30)   NOT NULL,
    required_rides            INT           NOT NULL,
    required_distance_km      NUMERIC(10,2) NOT NULL,
    free_ride_max_distance_km NUMERIC(10,2) NOT NULL,
    offer_validity_days       INT           NOT NULL,
    is_active                 BOOLEAN       NOT NULL DEFAULT true,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE(country_code, vehicle_type)
);

CREATE TABLE rider_progress (
    id                UUID          PRIMARY KEY,
    rider_id          UUID          NOT NULL,
    country_code      CHAR(2)       NOT NULL,
    vehicle_type      VARCHAR(30)   NOT NULL,
    ride_count        INT           NOT NULL DEFAULT 0,
    total_distance_km NUMERIC(10,2) NOT NULL DEFAULT 0,
    last_reset_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE(rider_id, country_code, vehicle_type)
);

CREATE TABLE free_ride_offers (
    id              UUID          PRIMARY KEY,
    rider_id        UUID          NOT NULL,
    country_code    CHAR(2)       NOT NULL,
    vehicle_type    VARCHAR(30)   NOT NULL,
    max_distance_km NUMERIC(10,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    earned_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ   NOT NULL,
    redeemed_at     TIMESTAMPTZ,
    ride_id         UUID,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_progress_rider ON rider_progress(rider_id, country_code);
CREATE INDEX idx_offers_rider ON free_ride_offers(rider_id, status);
CREATE INDEX idx_offers_expiry ON free_ride_offers(status, expires_at)
    WHERE status = 'AVAILABLE';
