# CLAUDE.md — Subscription Service

> Driver subscription bundle management for the Twende ride-hailing platform.
> Read this fully before writing any code in this module.
> Also read the root `CLAUDE.md` for platform-wide conventions.

---

## 1. What This Service Does

Manages driver subscription bundles (daily, weekly, monthly). When a driver activates a
bundle, they unlock the ability to go online and keep 100% of their earnings for the bundle
period. No subscription means no online status -- this is a hard block enforced by
driver-service via the internal API.

The subscription service coordinates with payment-service for billing and exposes an internal
endpoint for driver-service to check eligibility.

**Port:** 8090
**Database:** `twende_subscriptions`

---

## 2. API Endpoints

### Driver-facing (authenticated)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/subscriptions/plans` | Available plans for driver's country |
| `GET` | `/api/v1/subscriptions/me` | Driver's current active subscription |
| `POST` | `/api/v1/subscriptions/purchase` | Purchase a bundle |
| `GET` | `/api/v1/subscriptions/me/history` | Past subscriptions |

### Internal (service-to-service only)

| Method | Path | Description |
|---|---|---|
| `GET` | `/internal/subscriptions/{driverId}/active` | Check if driver has active subscription (used by driver-service go-online check) |

---

## 3. Database Schema

Migration file: `V1__create_subscription_schema.sql`

```sql
CREATE TYPE sub_status AS ENUM ('PENDING_PAYMENT','ACTIVE','EXPIRED','CANCELLED');

CREATE TABLE subscription_plans (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code   CHAR(2)       NOT NULL,
    plan_type      VARCHAR(10)   NOT NULL,  -- DAILY | WEEKLY | MONTHLY
    price          NUMERIC(10,2) NOT NULL,
    currency_code  VARCHAR(3)    NOT NULL,
    duration_hours INT           NOT NULL,  -- 24, 168, 720
    is_active      BOOLEAN       NOT NULL DEFAULT true,
    display_name   VARCHAR(100),            -- "Pakiti ya Siku"
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id      UUID        NOT NULL,
    country_code   CHAR(2)     NOT NULL,
    plan_id        UUID        NOT NULL REFERENCES subscription_plans(id),
    status         sub_status  NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_method VARCHAR(30) NOT NULL,
    amount_paid    NUMERIC(10,2),
    started_at     TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ,
    payment_ref    UUID,           -- reference to payment-service transaction
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sub_driver ON subscriptions(driver_id, status);
CREATE INDEX idx_sub_expires ON subscriptions(expires_at) WHERE status = 'ACTIVE';
```

### Seed Plans (Tanzania)

Migration file: `V2__seed_tanzania_plans.sql`

```sql
INSERT INTO subscription_plans (id, country_code, plan_type, price, currency_code, duration_hours, display_name)
VALUES
  (gen_random_uuid(), 'TZ', 'DAILY',   2000,  'TZS', 24,  'Pakiti ya Siku'),
  (gen_random_uuid(), 'TZ', 'WEEKLY',  10000, 'TZS', 168, 'Pakiti ya Wiki'),
  (gen_random_uuid(), 'TZ', 'MONTHLY', 35000, 'TZS', 720, 'Pakiti ya Mwezi');
```

---

## 4. Purchase Flow

```
1. Driver selects plan and payment method in app
2. POST /api/v1/subscriptions/purchase { planId, paymentMethod }
3. subscription-service:
   a. Validates plan exists and is active for driver's country
   b. Checks driver does not already have an active subscription
   c. Creates subscription record (status=PENDING_PAYMENT)
   d. Calls payment-service POST /internal/payments/subscription
   e. On payment success:
      - status -> ACTIVE
      - started_at = now()
      - expires_at = now() + plan.durationHours
      - payment_ref = payment transaction ID
      - Publishes SubscriptionActivatedEvent to Kafka
   f. On payment failure:
      - status -> CANCELLED
      - Returns error to driver
4. driver-service consumes SubscriptionActivatedEvent -> allows driver to go online
```

### Purchase Request

```json
POST /api/v1/subscriptions/purchase
{
  "planId": "uuid-of-plan",
  "paymentMethod": "MOBILE_MONEY"
}
```

---

## 5. Active Subscription Check

The internal endpoint is used by driver-service during the go-online flow to enforce the
hard block: no active subscription means no going online.

```java
public boolean hasActiveSubscription(UUID driverId) {
    return subscriptionRepository.existsByDriverIdAndStatusAndExpiresAtAfter(
        driverId, SubscriptionStatus.ACTIVE, Instant.now());
}
```

The `GET /internal/subscriptions/{driverId}/active` endpoint returns a simple boolean
response wrapped in `ApiResponse<Boolean>`.

---

## 6. Expiry Scheduler

Runs every 10 minutes to find and expire subscriptions that have passed their `expires_at`
timestamp.

```java
@Scheduled(fixedDelay = 600_000)  // every 10 minutes
public void expireSubscriptions() {
    List<Subscription> expired = subscriptionRepository
        .findByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, Instant.now());
    expired.forEach(sub -> {
        sub.setStatus(SubscriptionStatus.EXPIRED);
        subscriptionRepository.save(sub);
        kafkaTemplate.send("twende.subscriptions.expired",
            new SubscriptionExpiredEvent(sub.getDriverId(), sub.getCountryCode()));
    });
}
```

When a subscription expires, driver-service consumes the event and transitions the driver
to `OFFLINE` status if they are currently online.

---

## 7. Kafka Integration

### Published Topics

| Topic | Event | Trigger |
|---|---|---|
| `twende.subscriptions.activated` | `SubscriptionActivatedEvent` | Successful subscription purchase and payment |
| `twende.subscriptions.expired` | `SubscriptionExpiredEvent` | Expiry scheduler marks subscription as expired |

This service does not consume any Kafka topics.

---

## 8. Inter-Service Communication

**No Eureka. No Feign. Use Spring `RestClient` for all inter-service calls.**

### Dependencies on Other Services

| Service | What We Call | How |
|---|---|---|
| **payment-service** | `POST /internal/payments/subscription` -- initiate subscription payment | RestClient |

### Called By Other Services

| Service | What They Call | Purpose |
|---|---|---|
| **driver-service** | `GET /internal/subscriptions/{driverId}/active` | Go-online eligibility check |

---

## 9. Configuration

```yaml
server:
  port: 8090

spring:
  application:
    name: twende-subscription
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_subscriptions
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

twende:
  services:
    payment:
      base-url: ${PAYMENT_SERVICE_URL:http://localhost:8089}
```

---

## 10. Key Business Rules

1. **No subscription = no online** -- a driver with an expired or no subscription cannot
   set status to `ONLINE_AVAILABLE`. This is a hard block enforced by driver-service via
   the internal API. Never bypass this check.

2. **Driver keeps 100%** -- the subscription fee is the only revenue for Twende.
   Never deduct a percentage from ride earnings.

3. **One active subscription at a time** -- a driver cannot purchase a new subscription
   while they have an active one. Validate this before creating a new subscription record.

4. **Tanzania plans are fixed** -- DAILY TSh 2,000 (24h), WEEKLY TSh 10,000 (168h),
   MONTHLY TSh 35,000 (720h). Plans are seeded via migration and managed by admins only.

5. **Expiry is checked every 10 minutes** -- the scheduler runs at a fixed delay.
   Between scheduler runs, `hasActiveSubscription()` checks `expires_at > now()` in
   the query, so an expired subscription is effectively blocked immediately even before
   the scheduler marks it `EXPIRED`.

6. **Money arithmetic uses BigDecimal only** -- never `double`, never `float`.
   DB columns for money: `NUMERIC(10,2)`.

7. **All responses use `ApiResponse<T>` wrapper** -- consistent with the platform-wide
   response format.

8. **Multi-country ready** -- plans are scoped by `country_code`. When Kenya or Uganda
   launches, add new plan rows for those countries. No code changes needed.

---

## 11. Testing

### Unit Tests

- `SubscriptionService.purchase(...)` -- validates plan exists, no active subscription,
  calls payment-service, transitions to ACTIVE on success
- `SubscriptionService.hasActiveSubscription(...)` -- returns true/false correctly
  based on status and expiry
- `SubscriptionScheduler.expireSubscriptions()` -- finds and expires overdue subscriptions,
  publishes Kafka events
- Duplicate purchase prevention -- rejects purchase when active subscription exists

### Integration Tests

- Use Testcontainers for PostgreSQL and Kafka
- Mock payment-service responses with WireMock or MockRestServiceServer
- Test end-to-end purchase flow: request -> payment call -> status transition -> Kafka event
- Test expiry flow: create active subscription with past expiry -> run scheduler ->
  verify status=EXPIRED and Kafka event published
- Test internal active check endpoint: returns correct boolean for active/expired/none

### Test Naming

```java
@Test
void givenNoActiveSubscription_whenDriverPurchasesDailyPlan_thenSubscriptionActivated() { ... }

@Test
void givenActiveSubscription_whenDriverPurchasesAgain_thenBadRequestReturned() { ... }

@Test
void givenExpiredSubscription_whenActiveCheckCalled_thenReturnsFalse() { ... }

@Test
void givenActiveSubscriptionPastExpiry_whenSchedulerRuns_thenMarkedExpiredAndEventPublished() { ... }
```

### Coverage

- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw verify`
- Excluded from coverage: DTOs, enums, config classes

---

## Charter, Cargo & Flat Fee Expansion (Phase 7-9)

### Flat Fee as Alternative Revenue Model (Phase 7)

- Drivers choose between `SUBSCRIPTION` or `FLAT_FEE` as their revenue model
- `SUBSCRIPTION`: existing model -- pay daily/weekly/monthly bundle, keep 100% of earnings
- `FLAT_FEE`: no upfront bundle payment. Twende deducts a percentage from each trip's earnings

### Revenue Model Rules

- **Ride drivers** can switch revenue model monthly (not mid-month). Switch takes effect at start of next calendar month
- **Charter/cargo drivers** are always `FLAT_FEE` -- subscription option is not available for these service categories
- One active revenue model at a time per driver

### New Table: `flat_fee_configs`

- Schema: `country_code CHAR(2)`, `service_category VARCHAR(20)`, `percentage NUMERIC(5,2)`
- Example rows: `(TZ, RIDE, 15.00)`, `(TZ, CHARTER, 12.00)`, `(TZ, CARGO, 10.00)`
- Admin-managed, cached in Redis

### Updated Internal API

- `hasActiveRevenueModel(driverId)` replaces `hasActiveSubscription()` -- returns `true` if driver has active subscription OR is registered for flat fee
- Driver-service calls this updated check during go-online validation
- Response includes `revenueModel` type so driver-service knows which model is active

### New Endpoints

- `GET /api/v1/subscriptions/flat-fee/config` -- available flat fee rates for driver's country
- `POST /api/v1/subscriptions/revenue-model` -- switch revenue model (effective next month for ride drivers)
- `GET /internal/subscriptions/{driverId}/revenue-model` -- returns active revenue model type and details

---

## Implementation Steps

- [ ] 1. `application.yml` -- port 8090, datasource `twende_subscriptions`, Redis, Kafka (`consumer.group-id: twende-subscription`), payment-service URL (`http://localhost:8089`)
- [ ] 2. Entities: `SubscriptionPlan` (countryCode, planType, price, currencyCode, durationHours, displayName, isActive), `Subscription` (driverId, planId, status, paymentMethod, amountPaid, startedAt, expiresAt, paymentRef)
- [ ] 3. Repositories: `SubscriptionPlanRepository`, `SubscriptionRepository` -- include `existsByDriverIdAndStatusAndExpiresAtAfter`, `findByStatusAndExpiresAtBefore`
- [ ] 4. `SubscriptionService`: `getPlans(countryCode)`, `purchase(driverId, planId, paymentMethod)` -- validate plan exists + active + correct country, check no active subscription, create record as `PENDING_PAYMENT`, call payment-service `POST /internal/payments/subscription` via RestClient, on success set `ACTIVE` + timestamps, on failure set `CANCELLED`; `hasActiveSubscription(driverId)` -- boolean check
- [ ] 5. `ExpiryScheduler`: `@Scheduled(fixedDelay = 600_000)`, find `ACTIVE` subscriptions with `expiresAt < now()`, mark `EXPIRED`, publish Kafka event for each
- [ ] 6. Kafka producers: `twende.subscriptions.activated` (on successful purchase), `twende.subscriptions.expired` (on expiry)
- [ ] 7. `SubscriptionController` (driver-facing: GET plans, GET current, POST purchase, GET history) + internal endpoint `GET /internal/subscriptions/{driverId}/active` returning `ApiResponse<Boolean>`
- [ ] 8. Flyway migrations: `V1__create_subscription_schema.sql` (tables + indexes), `V2__seed_tanzania_plans.sql` (daily/weekly/monthly plans)
- [ ] 9. Dockerfile — Multi-stage build (eclipse-temurin:21-jdk-alpine for build, 21-jre-alpine for run). Non-root `twende` user. Health check on `/actuator/health`. Expose port 8090.
- [ ] 10. OpenAPI config — `OpenApiConfig.java` with SpringDoc `OpenAPI` bean. Title: "Subscription Service API". Swagger UI at `/swagger-ui.html`.
- [ ] 11. Unit tests + integration tests (Testcontainers for PostgreSQL and Kafka; WireMock for payment-service), verify >= 80% coverage with `./mvnw verify`
