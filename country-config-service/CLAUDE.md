# CLAUDE.md — Country Config Service

> Single source of truth for all per-country configuration in Twende.
> Read this fully before writing any code in this module.

---

## 1. Overview

The country-config-service owns all country-specific configuration: currencies, vehicle types,
pricing parameters, operating cities, payment methods, feature flags, provider assignments,
and regulatory settings. It enables Tanzania-first operation today and zero-code-change
expansion to Kenya, Uganda, and other markets later.

Config changes (e.g. toggling a feature flag, adjusting a fare, switching a provider) take
effect within seconds — Redis cache is evicted on write and a Kafka event is published so
all consuming services invalidate their local caches.

**Port:** 8082
**Database:** `twende_config` (PostgreSQL 16, Flyway-managed)
**Cache:** Redis 7 (5-minute TTL per country config)
**Event bus:** Kafka topic `twende.config.country-updated`

This service has **no** Eureka, Feign, or Spring Cloud Config Server dependencies.
It is a standalone Spring Boot application that other services call via REST.

---

## 2. Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Language |
| Spring Boot 4.0.x | Framework |
| Spring Data JPA + Hibernate | ORM |
| Spring Data Redis | Caching (5-min TTL) |
| Spring Security OAuth2 Resource Server | JWT validation for admin endpoints |
| Spring Kafka | Publish config-change events |
| PostgreSQL 16 | Primary database (`twende_config`) |
| Flyway | Schema migrations |
| Lombok | Boilerplate reduction |
| MapStruct | Entity-to-DTO mapping |

---

## 3. Package Structure

```
com.twende.countryconfig
├── CountryConfigServiceApplication.java
├── config/
│   ├── SecurityConfig.java           # OAuth2 resource server, public GETs, admin writes
│   ├── RedisConfig.java              # RedisTemplate + CacheManager (5-min TTL)
│   └── KafkaConfig.java              # KafkaTemplate for config-updated events
├── entity/
│   ├── CountryConfig.java            # PK = code CHAR(2), JSONB features column
│   ├── VehicleTypeConfig.java        # Pricing params per vehicle type per country
│   ├── OperatingCity.java            # Cities with provider columns + geo center/radius
│   ├── PaymentMethodConfig.java      # Payment methods per country (mobile_money, cash)
│   └── RequiredDriverDocument.java   # Document types required per country
├── repository/
│   ├── CountryConfigRepository.java
│   ├── VehicleTypeConfigRepository.java
│   ├── OperatingCityRepository.java
│   ├── PaymentMethodConfigRepository.java
│   └── RequiredDriverDocumentRepository.java
├── service/
│   └── CountryConfigService.java     # CRUD + caching + Kafka publish
├── controller/
│   └── CountryConfigController.java  # Public GETs + admin writes
├── dto/
│   ├── CountryConfigDto.java
│   ├── VehicleTypeConfigDto.java
│   ├── OperatingCityDto.java
│   ├── PaymentMethodConfigDto.java
│   ├── UpdateCountryConfigRequest.java
│   ├── UpdateFeaturesRequest.java
│   ├── CreateCityRequest.java
│   └── CountryConfigUpdatedEvent.java  # Kafka event payload
└── mapper/
    └── CountryConfigMapper.java      # MapStruct mapper
```

---

## 4. Database Schema

All migrations live in `src/main/resources/db/migration/`.

### V1__create_country_config_schema.sql

```sql
CREATE TYPE country_status AS ENUM ('ACTIVE', 'COMING_SOON', 'INACTIVE');

CREATE TABLE country_configs (
    code              CHAR(2)        PRIMARY KEY,
    name              VARCHAR(100)   NOT NULL,
    status            country_status NOT NULL DEFAULT 'COMING_SOON',

    -- Locale
    default_locale    VARCHAR(10)    NOT NULL,
    supported_locales TEXT[]         NOT NULL,
    date_format       VARCHAR(20)    NOT NULL DEFAULT 'DD/MM/YYYY',
    distance_unit     VARCHAR(5)     NOT NULL DEFAULT 'km',
    time_format       VARCHAR(5)     NOT NULL DEFAULT '12h',

    -- Currency
    currency_code     VARCHAR(3)     NOT NULL,
    currency_symbol   VARCHAR(5)     NOT NULL,
    minor_units       INT            NOT NULL DEFAULT 0,
    display_format    VARCHAR(20)    NOT NULL,

    -- Phone
    phone_prefix      VARCHAR(5)     NOT NULL,

    -- Payment
    cash_enabled            BOOLEAN  NOT NULL DEFAULT true,
    subscription_aggregator VARCHAR(50),

    -- Notification providers (per-country switching)
    sms_provider      VARCHAR(30)    NOT NULL DEFAULT 'AFRICASTALKING',
    push_provider     VARCHAR(30)    NOT NULL DEFAULT 'FCM',

    -- Regulatory
    regulatory_authority    VARCHAR(100),
    trip_reporting_required BOOLEAN  NOT NULL DEFAULT false,
    data_retention_days     INT      NOT NULL DEFAULT 365,

    -- Feature flags (JSONB for flexibility)
    features JSONB NOT NULL DEFAULT '{
        "ussdEnabled": false,
        "deliveryEnabled": false,
        "scheduledRidesEnabled": false,
        "surgeEnabled": false,
        "loyaltyEnabled": true,
        "corporateAccountsEnabled": false,
        "driverReferralsEnabled": true
    }',

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vehicle_type_configs (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code         CHAR(2)      NOT NULL REFERENCES country_configs(code),
    vehicle_type         VARCHAR(30)  NOT NULL,
    display_name         VARCHAR(50)  NOT NULL,
    max_passengers       INT          NOT NULL,
    icon_key             VARCHAR(50),
    is_active            BOOLEAN      NOT NULL DEFAULT true,

    -- Pricing
    base_fare            NUMERIC(12,2) NOT NULL,
    per_km               NUMERIC(12,2) NOT NULL,
    per_minute           NUMERIC(12,2) NOT NULL,
    minimum_fare         NUMERIC(12,2) NOT NULL,
    cancellation_fee     NUMERIC(12,2) NOT NULL DEFAULT 0,
    surge_multiplier_cap NUMERIC(4,2)  NOT NULL DEFAULT 2.5,

    required_docs TEXT[] NOT NULL DEFAULT '{}',

    UNIQUE(country_code, vehicle_type)
);

CREATE TABLE operating_cities (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code           CHAR(2)      NOT NULL REFERENCES country_configs(code),
    city_id                VARCHAR(50)  NOT NULL,
    name                   VARCHAR(100) NOT NULL,
    timezone               VARCHAR(50)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'COMING_SOON',
    center_lat             DOUBLE PRECISION NOT NULL,
    center_lng             DOUBLE PRECISION NOT NULL,
    radius_km              INT          NOT NULL,

    -- Per-city provider switching (location module reads these)
    geocoding_provider     VARCHAR(30)  NOT NULL DEFAULT 'GOOGLE',
    routing_provider       VARCHAR(30)  NOT NULL DEFAULT 'GOOGLE',
    autocomplete_provider  VARCHAR(30)  NOT NULL DEFAULT 'GOOGLE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(country_code, city_id)
);

CREATE TABLE payment_method_configs (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code CHAR(2)     NOT NULL REFERENCES country_configs(code),
    method_id    VARCHAR(30) NOT NULL,
    provider     VARCHAR(50) NOT NULL,
    is_enabled   BOOLEAN     NOT NULL DEFAULT true,
    display_name VARCHAR(50) NOT NULL,
    icon_key     VARCHAR(50),
    UNIQUE(country_code, method_id)
);

CREATE TABLE required_driver_documents (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code  CHAR(2)     NOT NULL REFERENCES country_configs(code),
    document_type VARCHAR(50) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    is_mandatory  BOOLEAN     NOT NULL DEFAULT true,
    UNIQUE(country_code, document_type)
);
```

### Key column notes

- **`country_configs.sms_provider`** — determines which `SmsProvider` implementation the
  notification service uses for this country. Valid: `AFRICASTALKING`, `TWILIO`, `BEEM`.
- **`country_configs.push_provider`** — determines which `PushProvider` implementation the
  notification service uses. Valid: `FCM`, `ONESIGNAL`.
- **`operating_cities.geocoding_provider`** — which geocoding backend the location module
  uses for this city. Valid: `GOOGLE`, `NOMINATIM`.
- **`operating_cities.routing_provider`** — routing backend. Valid: `GOOGLE`, `OSRM`.
- **`operating_cities.autocomplete_provider`** — autocomplete backend. Valid: `GOOGLE`, `NOMINATIM`.
- **`country_configs.features`** — JSONB column for feature flags. Read by other services
  to gate functionality (surge pricing, loyalty, etc.). Update via `PATCH /features` endpoint.

---

## 5. API Endpoints

### Public (no authentication required)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/config/{countryCode}` | Full country config including vehicle types, cities, payment methods |
| `GET` | `/api/v1/config/{countryCode}/vehicle-types` | Vehicle types and pricing for a country |
| `GET` | `/api/v1/config/{countryCode}/cities` | Operating cities for a country |
| `GET` | `/api/v1/config/active` | List of all active country codes (mobile app startup) |

### Admin only (`@PreAuthorize("hasRole('ADMIN')")`)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/config/admin` | All country configs including inactive |
| `POST` | `/api/v1/config` | Create new country config |
| `PUT` | `/api/v1/config/{countryCode}` | Update entire country config |
| `PATCH` | `/api/v1/config/{countryCode}/features` | Update feature flags only |
| `PATCH` | `/api/v1/config/{countryCode}/status` | Activate / deactivate a country |
| `PUT` | `/api/v1/config/{countryCode}/vehicle-types/{id}` | Update vehicle type config |
| `POST` | `/api/v1/config/{countryCode}/cities` | Add an operating city |

All responses use `ApiResponse<T>` wrapper from `common-lib`.
All write endpoints evict the Redis cache and publish a Kafka event.

---

## 6. Caching Strategy

Redis cache with 5-minute TTL. Cache key pattern: `country:config:{countryCode}`.

```java
@Service
public class CountryConfigService {

    private static final String CACHE_KEY = "country:config:";
    private static final Duration TTL = Duration.ofMinutes(5);

    public CountryConfigDto getConfig(String countryCode) {
        String key = CACHE_KEY + countryCode;

        // 1. Try Redis
        CountryConfigDto cached = redisTemplate.opsForValue().get(key);
        if (cached != null) return cached;

        // 2. Load from DB
        CountryConfig config = repository.findByCode(countryCode)
            .orElseThrow(() -> new ResourceNotFoundException("Country not found: " + countryCode));
        CountryConfigDto dto = mapper.toDto(config);

        // 3. Cache with TTL
        redisTemplate.opsForValue().set(key, dto, TTL);
        return dto;
    }

    public void updateConfig(String countryCode, UpdateCountryConfigRequest req) {
        // ... save to DB ...

        // Invalidate local cache
        redisTemplate.delete(CACHE_KEY + countryCode);

        // Broadcast to all services
        kafkaTemplate.send("twende.config.country-updated",
            new CountryConfigUpdatedEvent(countryCode));
    }
}
```

Each consuming service has a Kafka listener that calls `redisTemplate.delete(key)` on receipt
of `CountryConfigUpdatedEvent`, ensuring all services pick up changes within seconds.

---

## 7. Kafka Topics

### Published

| Topic | Event | Trigger |
|---|---|---|
| `twende.config.country-updated` | `CountryConfigUpdatedEvent` | Any config change (admin PUT/PATCH/POST) |

The event payload contains the `countryCode` that was updated. Consuming services use this
to invalidate their local Redis cache for that country.

### Consumed

None. This service is a pure publisher.

---

## 8. Tanzania Seed Data

Migration: `V2__seed_tanzania.sql`

### Country config
- Code: `TZ`, Name: `Tanzania`, Status: `ACTIVE`
- Locale: `sw-TZ` default, supported: `["sw-TZ", "en-TZ"]`
- Currency: `TZS`, symbol `TSh`, minor units `0`, format `TSh {amount}`
- Phone prefix: `+255`
- Cash enabled, subscription aggregator: `selcom`
- SMS provider: `AFRICASTALKING`, push provider: `FCM`
- Regulatory: `SUMATRA`, trip reporting required
- Features: `ussdEnabled: true`, `surgeEnabled: true`, `loyaltyEnabled: true`, `driverReferralsEnabled: true`

### Vehicle types (3)

| Type | Display | Passengers | Base | /km | /min | Min fare | Cancel fee |
|---|---|---|---|---|---|---|---|
| `BAJAJ` | Bajaj | 2 | 500 | 200 | 20 | 1,000 | 200 |
| `BODA_BODA` | Boda Boda | 1 | 300 | 150 | 15 | 700 | 150 |
| `CAR_ECONOMY` | Gari (Economy) | 4 | 1,000 | 500 | 50 | 3,000 | 500 |

All amounts in TZS (whole shillings, no decimals).

### Payment methods

| Method | Provider | Display |
|---|---|---|
| `mobile_money` | `selcom` | Pesa ya Simu |
| `cash` | `cash` | Taslimu |

### Operating cities (2)

| City | Status | Center | Radius |
|---|---|---|---|
| Dar es Salaam | `ACTIVE` | -6.7924, 39.2083 | 30 km |
| Arusha | `COMING_SOON` | -3.3869, 36.6830 | 20 km |

Both cities default to `GOOGLE` for geocoding, routing, and autocomplete providers.

### Seed SQL

```sql
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

INSERT INTO vehicle_type_configs
    (country_code, vehicle_type, display_name, max_passengers,
     base_fare, per_km, per_minute, minimum_fare, cancellation_fee)
VALUES
    ('TZ', 'BAJAJ',       'Bajaj',           2, 500,  200, 20, 1000, 200),
    ('TZ', 'BODA_BODA',   'Boda Boda',       1, 300,  150, 15, 700,  150),
    ('TZ', 'CAR_ECONOMY', 'Gari (Economy)',   4, 1000, 500, 50, 3000, 500);

INSERT INTO payment_method_configs (country_code, method_id, provider, display_name) VALUES
    ('TZ', 'mobile_money', 'selcom', 'Pesa ya Simu'),
    ('TZ', 'cash',         'cash',   'Taslimu');

INSERT INTO operating_cities
    (country_code, city_id, name, timezone, status, center_lat, center_lng, radius_km,
     geocoding_provider, routing_provider, autocomplete_provider)
VALUES
    ('TZ', 'dar-es-salaam', 'Dar es Salaam', 'Africa/Dar_es_Salaam',
     'ACTIVE', -6.7924, 39.2083, 30, 'GOOGLE', 'GOOGLE', 'GOOGLE'),
    ('TZ', 'arusha', 'Arusha', 'Africa/Dar_es_Salaam',
     'COMING_SOON', -3.3869, 36.6830, 20, 'GOOGLE', 'GOOGLE', 'GOOGLE');

INSERT INTO required_driver_documents (country_code, document_type, display_name, is_mandatory)
VALUES
    ('TZ', 'NATIONAL_ID',      'Kitambulisho cha Taifa',   true),
    ('TZ', 'DRIVING_LICENSE',   'Leseni ya Udereva',        true),
    ('TZ', 'VEHICLE_INSURANCE', 'Bima ya Gari',             true),
    ('TZ', 'TIN_CERTIFICATE',   'Namba ya TIN',             false);
```

---

## 9. Provider Switching

### Per-city providers (location/mapping)

Each `OperatingCity` row has three provider columns that control which backend the location
service uses for that city:

- `geocoding_provider` — `GOOGLE` or `NOMINATIM`
- `routing_provider` — `GOOGLE` or `OSRM`
- `autocomplete_provider` — `GOOGLE` or `NOMINATIM`

The location service's `ProviderFactory` reads these columns to resolve the correct
implementation. Changing a provider for one city does not affect other cities. This enables
gradual per-city migration (e.g. move Dar es Salaam routing to OSRM while Arusha stays on
Google).

### Per-country providers (notifications)

`CountryConfig` has two provider columns:

- `sms_provider` — `AFRICASTALKING`, `TWILIO`, or `BEEM`
- `push_provider` — `FCM` or `ONESIGNAL`

The notification service reads these to route SMS and push notifications through the correct
provider per country. Tanzania uses Africa's Talking + FCM. Kenya could use Twilio + FCM
without code changes.

---

## 10. Application Configuration

```yaml
server:
  port: 8082

spring:
  application:
    name: country-config-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_config
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      password: ${REDIS_PASSWORD:}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${AUTH_SERVICE_URL:http://auth-service:8081}/oauth2/jwks

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

logging:
  level:
    com.twende: DEBUG
    org.springframework.security: WARN
```

---

## 11. Key Rules

1. **Country config is read-only at runtime** — only admin endpoints can modify config.
   Services read it (with caching) but never write it.

2. **All money fields use `NUMERIC(12,2)` in SQL and `BigDecimal` in Java** — never
   `double`, never `float`, never `DOUBLE PRECISION` for monetary amounts.

3. **Every write operation must evict cache AND publish Kafka event** — cache eviction
   alone is not enough because other service instances hold their own Redis cache entries.

4. **Provider switching is per-city for mapping, per-country for notifications** —
   changing a provider for one city/country must not affect others.

5. **Feature flags live in JSONB** — the `features` column is intentionally unstructured
   to allow adding new flags without schema migrations.

6. **Public GET endpoints require no authentication** — mobile apps call these on startup
   to configure themselves. Admin write endpoints require `ADMIN` role.

7. **No Eureka, no Feign, no Config Server** — this service is called via plain REST.
   Other services use `RestClient` or `WebClient` to fetch config.

8. **Tanzania is the only active country at launch** — but the schema and code must
   support adding Kenya (`KE`) and Uganda (`UG`) with zero code changes (insert rows only).
