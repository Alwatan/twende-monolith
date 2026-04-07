-- V1__compliance_schema.sql
-- Compliance service schema: trip reports for regulatory submission and audit log

CREATE TABLE trip_reports (
    id               UUID        PRIMARY KEY,
    country_code     CHAR(2)     NOT NULL,
    ride_id          UUID        NOT NULL UNIQUE,
    driver_id        UUID        NOT NULL,
    rider_id         UUID        NOT NULL,
    vehicle_type     VARCHAR(30) NOT NULL,
    pickup_lat       NUMERIC(10,7) NOT NULL,
    pickup_lng       NUMERIC(10,7) NOT NULL,
    dropoff_lat      NUMERIC(10,7) NOT NULL,
    dropoff_lng      NUMERIC(10,7) NOT NULL,
    distance_metres  INT,
    duration_seconds INT,
    fare             NUMERIC(12,2),
    currency         VARCHAR(3),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    submitted        BOOLEAN     NOT NULL DEFAULT false,
    submitted_at     TIMESTAMPTZ,
    submission_ref   VARCHAR(200),
    submission_error TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_reports_pending ON trip_reports(country_code, submitted, created_at)
    WHERE submitted = false;
CREATE INDEX idx_trip_reports_country ON trip_reports(country_code, created_at DESC);
CREATE INDEX idx_trip_reports_driver  ON trip_reports(driver_id, created_at DESC);

CREATE TABLE audit_log (
    id           UUID        PRIMARY KEY,
    country_code CHAR(2)     NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    entity_id    UUID,
    actor_id     UUID,
    payload      JSONB,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_country_type ON audit_log(country_code, event_type, occurred_at DESC);
CREATE INDEX idx_audit_log_entity       ON audit_log(entity_id, occurred_at DESC);
CREATE INDEX idx_audit_log_actor        ON audit_log(actor_id, occurred_at DESC);
