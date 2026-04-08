-- =============================================================================
-- V2__seed_plans.sql
-- Seed Tanzania subscription plans: 3 vehicle types x 3 durations = 9 plans
-- =============================================================================

-- Boda Boda plans (lowest earning, cheapest bundles)
INSERT INTO subscription_plans (id, country_code, vehicle_type, plan_type, price, currency_code, duration_hours, display_name)
VALUES
    ('a1b2c3d4-0001-4000-8000-000000000001', 'TZ', 'BODA_BODA', 'DAILY',    1000, 'TZS',  24, 'Boda - Pakiti ya Siku'),
    ('a1b2c3d4-0002-4000-8000-000000000002', 'TZ', 'BODA_BODA', 'WEEKLY',   5000, 'TZS', 168, 'Boda - Pakiti ya Wiki'),
    ('a1b2c3d4-0003-4000-8000-000000000003', 'TZ', 'BODA_BODA', 'MONTHLY', 18000, 'TZS', 720, 'Boda - Pakiti ya Mwezi');

-- Bajaj plans (mid-tier earning)
INSERT INTO subscription_plans (id, country_code, vehicle_type, plan_type, price, currency_code, duration_hours, display_name)
VALUES
    ('a1b2c3d4-0004-4000-8000-000000000004', 'TZ', 'BAJAJ', 'DAILY',    2000,  'TZS',  24, 'Bajaj - Pakiti ya Siku'),
    ('a1b2c3d4-0005-4000-8000-000000000005', 'TZ', 'BAJAJ', 'WEEKLY',  10000,  'TZS', 168, 'Bajaj - Pakiti ya Wiki'),
    ('a1b2c3d4-0006-4000-8000-000000000006', 'TZ', 'BAJAJ', 'MONTHLY', 30000,  'TZS', 720, 'Bajaj - Pakiti ya Mwezi');

-- Economy Car plans (highest earning, premium bundles)
INSERT INTO subscription_plans (id, country_code, vehicle_type, plan_type, price, currency_code, duration_hours, display_name)
VALUES
    ('a1b2c3d4-0007-4000-8000-000000000007', 'TZ', 'CAR_ECONOMY', 'DAILY',    3500,  'TZS',  24, 'Gari - Pakiti ya Siku'),
    ('a1b2c3d4-0008-4000-8000-000000000008', 'TZ', 'CAR_ECONOMY', 'WEEKLY',  18000,  'TZS', 168, 'Gari - Pakiti ya Wiki'),
    ('a1b2c3d4-0009-4000-8000-000000000009', 'TZ', 'CAR_ECONOMY', 'MONTHLY', 55000,  'TZS', 720, 'Gari - Pakiti ya Mwezi');
