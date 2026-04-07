-- V2__seed_loyalty_rules.sql
-- Tanzania loyalty rules: thresholds for earning free rides per vehicle type

INSERT INTO loyalty_rules (id, country_code, vehicle_type, required_rides, required_distance_km,
    free_ride_max_distance_km, offer_validity_days, is_active) VALUES
  (gen_random_uuid(), 'TZ', 'BAJAJ',       20,  100.00,  5.00, 30, true),
  (gen_random_uuid(), 'TZ', 'BODA_BODA',   25,  150.00,  5.00, 30, true),
  (gen_random_uuid(), 'TZ', 'ECONOMY_CAR', 15,   80.00, 10.00, 30, true);
