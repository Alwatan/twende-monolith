-- Tanzania seed data

-- Country config
INSERT INTO country_configs (code, name, status, default_locale, supported_locales,
    currency_code, currency_symbol, minor_units, display_format,
    phone_prefix, cash_enabled, subscription_aggregator,
    sms_provider, push_provider,
    regulatory_authority, trip_reporting_required, features)
VALUES ('TZ', 'Tanzania', 'ACTIVE', 'sw-TZ', '{"sw-TZ","en-TZ"}',
    'TZS', 'TSh', 0, 'TSh {amount}',
    '+255', true, 'selcom',
    'AFRICASTALKING', 'FCM',
    'SUMATRA', true,
    '{"ussdEnabled":true,"deliveryEnabled":false,"scheduledRidesEnabled":false,
      "surgeEnabled":true,"loyaltyEnabled":true,"corporateAccountsEnabled":false,
      "driverReferralsEnabled":true}');

-- Vehicle types (3): BAJAJ, BODA_BODA, CAR_ECONOMY
-- UUIDs are deterministic for seed data reproducibility
INSERT INTO vehicle_type_configs
    (id, country_code, vehicle_type, display_name, max_passengers,
     base_fare, per_km, per_minute, minimum_fare, cancellation_fee)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'TZ', 'BAJAJ',       'Bajaj',           2, 500,  200, 20, 1000, 200),
    ('00000000-0000-0000-0000-000000000002', 'TZ', 'BODA_BODA',   'Boda Boda',       1, 300,  150, 15, 700,  150),
    ('00000000-0000-0000-0000-000000000003', 'TZ', 'CAR_ECONOMY', 'Gari (Economy)',   4, 1000, 500, 50, 3000, 500);

-- Operating cities (2): Dar es Salaam (active), Arusha (coming soon)
INSERT INTO operating_cities
    (id, country_code, city_id, name, timezone, status, center_lat, center_lng, radius_km,
     geocoding_provider, routing_provider, autocomplete_provider)
VALUES
    ('00000000-0000-0000-0000-000000000010', 'TZ', 'dar-es-salaam', 'Dar es Salaam', 'Africa/Dar_es_Salaam',
     'ACTIVE', -6.7924, 39.2083, 30, 'GOOGLE', 'GOOGLE', 'GOOGLE'),
    ('00000000-0000-0000-0000-000000000011', 'TZ', 'arusha', 'Arusha', 'Africa/Dar_es_Salaam',
     'COMING_SOON', -3.3869, 36.6830, 20, 'GOOGLE', 'GOOGLE', 'GOOGLE');

-- Payment methods (2): mobile money (Selcom) and cash
INSERT INTO payment_method_configs (id, country_code, method_id, provider, display_name) VALUES
    ('00000000-0000-0000-0000-000000000020', 'TZ', 'mobile_money', 'selcom', 'Pesa ya Simu'),
    ('00000000-0000-0000-0000-000000000021', 'TZ', 'cash',         'cash',   'Taslimu');

-- Required driver documents (4)
INSERT INTO required_driver_documents (id, country_code, document_type, display_name, is_mandatory)
VALUES
    ('00000000-0000-0000-0000-000000000030', 'TZ', 'NATIONAL_ID',      'Kitambulisho cha Taifa',   true),
    ('00000000-0000-0000-0000-000000000031', 'TZ', 'DRIVING_LICENSE',   'Leseni ya Udereva',        true),
    ('00000000-0000-0000-0000-000000000032', 'TZ', 'VEHICLE_INSURANCE', 'Bima ya Gari',             true),
    ('00000000-0000-0000-0000-000000000033', 'TZ', 'TIN_CERTIFICATE',   'Namba ya TIN',             false);
