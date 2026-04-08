-- Flat fee percentage configuration per country per service category
CREATE TABLE flat_fee_configs (
    id               UUID          PRIMARY KEY,
    country_code     CHAR(2)       NOT NULL REFERENCES country_configs(code),
    service_category VARCHAR(20)   NOT NULL,
    percentage       NUMERIC(5,2)  NOT NULL,
    is_active        BOOLEAN       NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE(country_code, service_category)
);

-- Seed Tanzania flat fee rates
INSERT INTO flat_fee_configs (id, country_code, service_category, percentage) VALUES
  (gen_random_uuid(), 'TZ', 'RIDE', 15.00),
  (gen_random_uuid(), 'TZ', 'CHARTER', 12.00),
  (gen_random_uuid(), 'TZ', 'CARGO', 10.00);

-- Add charter/cargo feature flags to existing Tanzania config
UPDATE country_configs SET features = features || '{"charterEnabled": false, "cargoEnabled": false}'::jsonb
WHERE code = 'TZ';
