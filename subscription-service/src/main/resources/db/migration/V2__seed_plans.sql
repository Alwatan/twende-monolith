-- =============================================================================
-- V2__seed_plans.sql
-- Seed Tanzania subscription plans with deterministic UUIDs
-- =============================================================================

INSERT INTO subscription_plans (id, country_code, plan_type, price, currency_code, duration_hours, display_name)
VALUES
    ('a1b2c3d4-0001-4000-8000-000000000001', 'TZ', 'DAILY',    2000,  'TZS',  24, 'Pakiti ya Siku'),
    ('a1b2c3d4-0002-4000-8000-000000000002', 'TZ', 'WEEKLY',  10000,  'TZS', 168, 'Pakiti ya Wiki'),
    ('a1b2c3d4-0003-4000-8000-000000000003', 'TZ', 'MONTHLY', 35000,  'TZS', 720, 'Pakiti ya Mwezi');
