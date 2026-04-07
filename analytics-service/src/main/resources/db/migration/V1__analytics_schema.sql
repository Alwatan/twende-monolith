-- V1__analytics_schema.sql
-- Analytics service schema: append-only event store and materialised daily summaries

CREATE TABLE analytics_events (
    id           UUID        PRIMARY KEY,
    country_code CHAR(2)     NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    entity_id    UUID,
    actor_id     UUID,
    payload      JSONB,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_type    ON analytics_events(event_type, occurred_at DESC);
CREATE INDEX idx_events_actor   ON analytics_events(actor_id, occurred_at DESC);
CREATE INDEX idx_events_country ON analytics_events(country_code, occurred_at DESC);

CREATE TABLE driver_daily_summaries (
    id           UUID          PRIMARY KEY,
    driver_id    UUID          NOT NULL,
    country_code CHAR(2)       NOT NULL,
    date         DATE          NOT NULL,
    total_earned NUMERIC(12,2) NOT NULL DEFAULT 0,
    trip_count   INT           NOT NULL DEFAULT 0,
    online_hours NUMERIC(5,2)  NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE(driver_id, date)
);

CREATE INDEX idx_driver_summaries_country ON driver_daily_summaries(country_code, date DESC);
CREATE INDEX idx_driver_summaries_driver  ON driver_daily_summaries(driver_id, date DESC);
