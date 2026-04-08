-- Driver revenue model tracking
CREATE TABLE driver_revenue_models (
    id               UUID          PRIMARY KEY,
    driver_id        UUID          NOT NULL UNIQUE,
    country_code     CHAR(2)       NOT NULL,
    revenue_model    VARCHAR(20)   NOT NULL DEFAULT 'SUBSCRIPTION',
    service_category VARCHAR(20)   NOT NULL DEFAULT 'RIDE',
    registered_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_revenue_model_driver ON driver_revenue_models(driver_id);
