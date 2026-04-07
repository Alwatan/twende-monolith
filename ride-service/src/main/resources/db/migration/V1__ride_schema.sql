-- =============================================================================
-- V1__ride_schema.sql
-- Ride service schema: rides, ride_status_events, ride_driver_rejections
-- =============================================================================

CREATE TABLE rides (
    id                        UUID          PRIMARY KEY,
    country_code              CHAR(2)       NOT NULL,
    rider_id                  UUID          NOT NULL,
    driver_id                 UUID,
    vehicle_type              VARCHAR(30)   NOT NULL,
    status                    VARCHAR(30)   NOT NULL DEFAULT 'REQUESTED',
    city_id                   UUID,

    -- Pickup
    pickup_lat                NUMERIC(10,7) NOT NULL,
    pickup_lng                NUMERIC(10,7) NOT NULL,
    pickup_address            VARCHAR(300)  NOT NULL,

    -- Dropoff
    dropoff_lat               NUMERIC(10,7) NOT NULL,
    dropoff_lng               NUMERIC(10,7) NOT NULL,
    dropoff_address           VARCHAR(300)  NOT NULL,

    -- Fare
    estimated_fare            NUMERIC(12,2) NOT NULL,
    fare_boost_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    final_fare                NUMERIC(12,2),
    currency_code             VARCHAR(3)    NOT NULL,

    -- Loyalty
    free_ride                 BOOLEAN       NOT NULL DEFAULT FALSE,
    free_ride_offer_id        UUID,

    -- Rejection tracking
    driver_rejection_count    INT           NOT NULL DEFAULT 0,

    -- Trip start OTP
    trip_start_otp_hash       VARCHAR(100),
    trip_start_otp_expires_at TIMESTAMPTZ,
    trip_start_otp_attempts   INT           NOT NULL DEFAULT 0,

    -- Trip metrics
    distance_metres           INT,
    duration_seconds          INT,

    -- Timestamps
    requested_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    assigned_at               TIMESTAMPTZ,
    arrived_at                TIMESTAMPTZ,
    started_at                TIMESTAMPTZ,
    completed_at              TIMESTAMPTZ,
    cancelled_at              TIMESTAMPTZ,
    cancel_reason             TEXT,
    cancelled_by              VARCHAR(10),
    matching_timeout_at       TIMESTAMPTZ,

    -- Audit
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_rides_rider   ON rides(rider_id, status);
CREATE INDEX idx_rides_driver  ON rides(driver_id, status);
CREATE INDEX idx_rides_status  ON rides(status);
CREATE INDEX idx_rides_country ON rides(country_code, status);
CREATE INDEX idx_rides_requested ON rides(requested_at);

-- -----------------------------------------------------------------------------
-- ride_status_events — audit log for every ride state transition
-- -----------------------------------------------------------------------------
CREATE TABLE ride_status_events (
    id          UUID        PRIMARY KEY,
    ride_id     UUID        NOT NULL REFERENCES rides(id),
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    actor_id    UUID,
    actor_role  VARCHAR(10),
    metadata    JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_status_events_ride ON ride_status_events(ride_id);

-- -----------------------------------------------------------------------------
-- ride_driver_rejections — tracks which drivers rejected which rides
-- -----------------------------------------------------------------------------
CREATE TABLE ride_driver_rejections (
    id          UUID        PRIMARY KEY,
    ride_id     UUID        NOT NULL REFERENCES rides(id),
    driver_id   UUID        NOT NULL,
    rejected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rejections_ride ON ride_driver_rejections(ride_id);
