-- Add revenue model and service categories to driver profile
ALTER TABLE drivers ADD COLUMN revenue_model VARCHAR(20) NOT NULL DEFAULT 'SUBSCRIPTION';
ALTER TABLE drivers ADD COLUMN quality_tier VARCHAR(20);

-- Service categories join table
CREATE TABLE driver_service_categories (
    driver_id        UUID         NOT NULL REFERENCES drivers(id),
    service_category VARCHAR(20)  NOT NULL,
    PRIMARY KEY (driver_id, service_category)
);
