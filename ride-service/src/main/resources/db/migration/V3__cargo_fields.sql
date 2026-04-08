-- V3: Add cargo-specific fields to rides table

ALTER TABLE rides ADD COLUMN cargo_description TEXT;
ALTER TABLE rides ADD COLUMN weight_tier VARCHAR(10);
ALTER TABLE rides ADD COLUMN driver_provides_loading BOOLEAN NOT NULL DEFAULT false;
