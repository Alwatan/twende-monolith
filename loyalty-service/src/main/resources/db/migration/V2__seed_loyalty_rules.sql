-- V2__seed_loyalty_rules.sql
-- Tanzania loyalty rules: thresholds for earning free rides per vehicle type
--
-- Boda Boda: most frequent, short trips -> 30 rides, 90km, 3km free ride, 5-day validity
-- Bajaj: moderate frequency -> 20 rides, 100km, 5km free ride, 7-day validity
-- Economy Car: less frequent, longer trips -> 12 rides, 120km, 10km free ride, 10-day validity

INSERT INTO loyalty_rules (id, country_code, vehicle_type, required_rides, required_distance_km,
    free_ride_max_distance_km, offer_validity_days, is_active) VALUES
  (gen_random_uuid(), 'TZ', 'BODA_BODA',   30,   90.00,  3.00,  5, true),
  (gen_random_uuid(), 'TZ', 'BAJAJ',       20,  100.00,  5.00,  7, true),
  (gen_random_uuid(), 'TZ', 'CAR_ECONOMY', 12,  120.00, 10.00, 10, true);
