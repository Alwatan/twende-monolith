-- V6: Add cargo vehicle types for Tanzania

-- Add weight_tier_surcharges JSONB column (nullable, only for cargo vehicles)
ALTER TABLE vehicle_type_configs ADD COLUMN weight_tier_surcharges JSONB;

-- Seed Tanzania cargo vehicle types
-- Cargo pricing: baseFare + (distanceKm * perKm) + weightTierSurcharge — NO per_minute, NO per_hour
INSERT INTO vehicle_type_configs (id, country_code, vehicle_type, display_name, max_passengers,
    base_fare, per_km, per_minute, minimum_fare, cancellation_fee, surge_multiplier_cap,
    weight_tier_surcharges, is_active, required_docs, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'TZ', 'CARGO_TUKTUK', 'Cargo Tuk-tuk', 0,
   5000, 800, 0, 5000, 2000, 1.0,
   '{"LIGHT": 0, "MEDIUM": 2000, "FULL": 5000}',
   true, '{}', now(), now()),
  (gen_random_uuid(), 'TZ', 'TRUCK_LIGHT', 'Light Truck (up to 3t)', 0,
   15000, 1500, 0, 15000, 5000, 1.0,
   '{"LIGHT": 0, "MEDIUM": 5000, "FULL": 10000}',
   true, '{}', now(), now()),
  (gen_random_uuid(), 'TZ', 'TRUCK_MEDIUM', 'Medium Truck (3-10t)', 0,
   30000, 2500, 0, 30000, 10000, 1.0,
   '{"LIGHT": 0, "MEDIUM": 10000, "FULL": 20000}',
   true, '{}', now(), now()),
  (gen_random_uuid(), 'TZ', 'TRUCK_HEAVY', 'Heavy Truck (10t+)', 0,
   50000, 3500, 0, 50000, 15000, 1.0,
   '{"LIGHT": 0, "MEDIUM": 15000, "FULL": 30000}',
   true, '{}', now(), now());
