-- =============================================================================
-- V1__subscription_schema.sql
-- Subscription service schema: subscription_plans, subscriptions
-- =============================================================================

CREATE TABLE subscription_plans (
    id             UUID          PRIMARY KEY,
    country_code   CHAR(2)       NOT NULL,
    vehicle_type   VARCHAR(30)   NOT NULL,
    plan_type      VARCHAR(10)   NOT NULL,
    price          NUMERIC(10,2) NOT NULL,
    currency_code  VARCHAR(3)    NOT NULL,
    duration_hours INT           NOT NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    display_name   VARCHAR(100),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE(country_code, vehicle_type, plan_type)
);

CREATE INDEX idx_plans_country ON subscription_plans(country_code, is_active);

-- -----------------------------------------------------------------------------
-- subscriptions -- one active subscription per driver at a time
-- -----------------------------------------------------------------------------
CREATE TABLE subscriptions (
    id             UUID          PRIMARY KEY,
    driver_id      UUID          NOT NULL,
    country_code   CHAR(2)       NOT NULL,
    plan_id        UUID          NOT NULL REFERENCES subscription_plans(id),
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_method VARCHAR(30)   NOT NULL,
    amount_paid    NUMERIC(10,2),
    started_at     TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ,
    payment_ref    UUID,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_sub_driver  ON subscriptions(driver_id, status);
CREATE INDEX idx_sub_expires ON subscriptions(expires_at) WHERE status = 'ACTIVE';
