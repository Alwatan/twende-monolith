-- Charter vehicle type support: add quality_tier, per_hour, and quality_tier_surcharge columns
ALTER TABLE vehicle_type_configs ADD COLUMN quality_tier VARCHAR(20);
ALTER TABLE vehicle_type_configs ADD COLUMN per_hour NUMERIC(12,2);
ALTER TABLE vehicle_type_configs ADD COLUMN quality_tier_surcharge NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Drop the unique constraint to allow same vehicle type with different quality tiers
-- (existing BAJAJ, BODA_BODA, CAR_ECONOMY have no quality_tier so remain unique)

-- Seed Tanzania charter vehicle types
INSERT INTO vehicle_type_configs (id, country_code, vehicle_type, display_name, max_passengers,
    base_fare, per_km, per_minute, minimum_fare, cancellation_fee, surge_multiplier_cap,
    quality_tier, per_hour, quality_tier_surcharge, is_active, required_docs, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'TZ', 'MINIBUS_STANDARD', 'Minibus Standard', 18,
   50000, 1500, 0, 50000, 10000, 2.0, 'STANDARD', 15000, 0, true, '{}', now(), now()),
  (gen_random_uuid(), 'TZ', 'MINIBUS_LUXURY', 'Minibus Luxury', 18,
   80000, 2000, 0, 80000, 15000, 2.0, 'LUXURY', 20000, 30000, true, '{}', now(), now()),
  (gen_random_uuid(), 'TZ', 'BUS_STANDARD', 'Bus Standard', 50,
   150000, 2500, 0, 150000, 25000, 2.0, 'STANDARD', 25000, 0, true, '{}', now(), now()),
  (gen_random_uuid(), 'TZ', 'BUS_LUXURY', 'Bus Luxury', 50,
   250000, 3500, 0, 250000, 40000, 2.0, 'LUXURY', 35000, 50000, true, '{}', now(), now());
