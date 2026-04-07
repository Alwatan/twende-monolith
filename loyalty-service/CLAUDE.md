# CLAUDE.md -- Loyalty Service

> Twende Platform loyalty-service. Read this file fully before writing any code.

---

## 1. Overview

Rider loyalty programme service. Tracks ride count and mileage per vehicle type for each
rider. Awards free ride offers when configurable thresholds are met. Automatically coordinates
with ride-service and payment-service so that drivers are paid in full for free rides (Twende
absorbs the cost).

**Port:** 8095
**Database:** `twende_loyalty`
**Base package:** `tz.co.twende.loyalty`
**Shared library:** `tz.co.twende.common` (common-lib dependency)

This is a standalone Spring Boot microservice in the Twende monorepo. It does NOT use Eureka,
Feign, or Spring Cloud. Inter-service communication uses Spring `RestClient` with direct URLs
resolved from configuration. Authentication context arrives via gateway-injected headers.

---

## 2. Package Structure

```
tz.co.twende.loyalty
├── LoyaltyServiceApplication.java
├── config/
│   ├── RedisConfig.java
│   ├── KafkaConfig.java
│   ├── JpaConfig.java                # @EnableJpaAuditing
│   ├── AsyncConfig.java              # @EnableAsync thread pool
│   ├── SchedulingConfig.java         # @EnableScheduling
│   └── OpenApiConfig.java
├── entity/
│   ├── LoyaltyRule.java              # extends BaseEntity
│   ├── RiderProgress.java            # extends BaseEntity
│   └── FreeRideOffer.java            # extends BaseEntity
├── repository/
│   ├── LoyaltyRuleRepository.java
│   ├── RiderProgressRepository.java
│   └── FreeRideOfferRepository.java
├── service/
│   ├── LoyaltyService.java           # Progress tracking, offer award, offer query
│   ├── OfferRedemptionService.java   # Redeem and expire offers
│   └── OfferExpiryScheduler.java     # Scheduled job to expire stale offers
├── kafka/
│   ├── LoyaltyEventConsumer.java     # Consumes twende.rides.completed
│   └── LoyaltyEventPublisher.java    # Publishes twende.loyalty.free-ride-earned
├── controller/
│   ├── LoyaltyController.java        # /api/v1/loyalty/** (rider-facing + admin)
│   └── LoyaltyInternalController.java # /internal/loyalty/** (service-to-service)
├── dto/
│   ├── request/
│   │   └── UpdateLoyaltyRuleRequest.java
│   ├── response/
│   │   ├── RiderProgressDto.java
│   │   ├── FreeRideOfferDto.java
│   │   └── LoyaltyRuleDto.java
│   └── event/
│       ├── RideCompletedEvent.java
│       └── FreeRideOfferEarnedEvent.java
└── mapper/
    └── LoyaltyMapper.java            # MapStruct entity <-> DTO
```

---

## 3. Database Schema

Database: `twende_loyalty` (isolated per-service database).
Schema managed by Flyway. Migrations in `src/main/resources/db/migration/`.

```sql
-- V1__create_loyalty_schema.sql

CREATE TABLE loyalty_rules (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code            CHAR(2)       NOT NULL,
    vehicle_type            VARCHAR(30)   NOT NULL,  -- BAJAJ, BODA_BODA, ECONOMY_CAR
    required_rides          INT           NOT NULL,
    required_distance_km    NUMERIC(10,2) NOT NULL,
    free_ride_max_distance_km NUMERIC(10,2) NOT NULL,
    offer_validity_days     INT           NOT NULL,
    is_active               BOOLEAN       NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE(country_code, vehicle_type)
);

CREATE TABLE rider_progress (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id          UUID          NOT NULL,
    country_code      CHAR(2)       NOT NULL,
    vehicle_type      VARCHAR(30)   NOT NULL,
    ride_count        INT           NOT NULL DEFAULT 0,
    total_distance_km NUMERIC(10,2) NOT NULL DEFAULT 0,
    last_reset_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE(rider_id, country_code, vehicle_type)
);

CREATE TABLE free_ride_offers (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id          UUID          NOT NULL,
    country_code      CHAR(2)       NOT NULL,
    vehicle_type      VARCHAR(30)   NOT NULL,
    max_distance_km   NUMERIC(10,2) NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE, REDEEMED, EXPIRED
    earned_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ   NOT NULL,
    redeemed_at       TIMESTAMPTZ,
    ride_id           UUID,          -- FK to ride that redeemed this offer
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_progress_rider ON rider_progress(rider_id, country_code);
CREATE INDEX idx_offers_rider ON free_ride_offers(rider_id, status);
CREATE INDEX idx_offers_expiry ON free_ride_offers(status, expires_at)
    WHERE status = 'AVAILABLE';
```

### Seed Data (Tanzania)

```sql
-- V2__seed_loyalty_rules.sql

INSERT INTO loyalty_rules (country_code, vehicle_type, required_rides, required_distance_km,
    free_ride_max_distance_km, offer_validity_days, is_active) VALUES
  ('TZ', 'BAJAJ',       20,  100.00, 5.00,  30, true),
  ('TZ', 'BODA_BODA',   25,  150.00, 5.00,  30, true),
  ('TZ', 'ECONOMY_CAR', 15,   80.00, 10.00, 30, true);
```

---

## 4. Key Entities

### LoyaltyRule

Admin-configurable per country + vehicle type. Defines the threshold to earn a free ride.

| Field | Type | Description |
|---|---|---|
| countryCode | CHAR(2) | Country this rule applies to |
| vehicleType | VARCHAR(30) | Vehicle type (BAJAJ, BODA_BODA, ECONOMY_CAR) |
| requiredRides | INT | Number of rides needed to earn free ride |
| requiredDistanceKm | NUMERIC(10,2) | Minimum total distance (km) needed |
| freeRideMaxDistanceKm | NUMERIC(10,2) | Maximum distance allowed for the free ride |
| offerValidityDays | INT | How many days the offer is valid |
| isActive | BOOLEAN | Whether this rule is currently active |

### RiderProgress

Running tally per rider per vehicle type. Resets when a free ride offer is earned.

| Field | Type | Description |
|---|---|---|
| riderId | UUID | Rider who is accumulating progress |
| countryCode | CHAR(2) | Country |
| vehicleType | VARCHAR(30) | Vehicle type being tracked |
| rideCount | INT | Number of paid rides completed |
| totalDistanceKm | NUMERIC(10,2) | Total distance of paid rides |
| lastResetAt | TIMESTAMPTZ | When progress was last reset (after earning an offer) |

### FreeRideOffer

The actual offer. One offer per threshold reached. Can be redeemed on exactly one ride.

| Field | Type | Description |
|---|---|---|
| riderId | UUID | Rider who earned this offer |
| countryCode | CHAR(2) | Country |
| vehicleType | VARCHAR(30) | Must match ride vehicle type for redemption |
| maxDistanceKm | NUMERIC(10,2) | Max trip distance for this offer |
| status | VARCHAR(20) | AVAILABLE, REDEEMED, or EXPIRED |
| earnedAt | TIMESTAMPTZ | When the offer was earned |
| expiresAt | TIMESTAMPTZ | When the offer expires if not used |
| redeemedAt | TIMESTAMPTZ | When the offer was redeemed (null if not) |
| rideId | UUID | The ride that used this offer (null if not redeemed) |

---

## 5. Kafka Topics

### Consumed

| Topic | Event Payload | Action |
|---|---|---|
| `twende.rides.completed` | `RideCompletedEvent` | Increment rider progress, check threshold, award offer if met |

**Consumer group:** `loyalty-service-group`

### Published

| Topic | Event Payload | Trigger |
|---|---|---|
| `twende.loyalty.free-ride-earned` | `FreeRideOfferEarnedEvent` | When a rider earns a new free ride offer |

---

## 6. Service Logic

### Progress Tracking (on RideCompletedEvent)

```java
@KafkaListener(topics = "twende.rides.completed", groupId = "loyalty-service-group")
public void onRideCompleted(RideCompletedEvent event) {
    // Free rides do NOT count toward the next offer
    if (event.isFreeRide()) return;

    RiderProgress progress = progressRepository
        .findByRiderIdAndCountryCodeAndVehicleType(
            event.getRiderId(), event.getCountryCode(), event.getVehicleType())
        .orElseGet(() -> new RiderProgress(
            event.getRiderId(), event.getCountryCode(), event.getVehicleType()));

    progress.setRideCount(progress.getRideCount() + 1);
    BigDecimal tripKm = new BigDecimal(event.getDistanceMetres())
        .divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
    progress.setTotalDistanceKm(progress.getTotalDistanceKm().add(tripKm));
    progressRepository.save(progress);

    // Check if threshold reached
    checkAndAwardOffer(progress);
}
```

### Threshold Check and Award

```java
private void checkAndAwardOffer(RiderProgress progress) {
    LoyaltyRule rule = ruleRepository
        .findByCountryCodeAndVehicleTypeAndIsActiveTrue(
            progress.getCountryCode(), progress.getVehicleType())
        .orElse(null);

    if (rule == null) return;

    if (progress.getRideCount() >= rule.getRequiredRides()
            && progress.getTotalDistanceKm().compareTo(rule.getRequiredDistanceKm()) >= 0) {
        awardFreeRide(progress, rule);
    }
}

private void awardFreeRide(RiderProgress progress, LoyaltyRule rule) {
    FreeRideOffer offer = new FreeRideOffer();
    offer.setRiderId(progress.getRiderId());
    offer.setCountryCode(progress.getCountryCode());
    offer.setVehicleType(progress.getVehicleType());
    offer.setMaxDistanceKm(rule.getFreeRideMaxDistanceKm());
    offer.setStatus(OfferStatus.AVAILABLE);
    offer.setEarnedAt(Instant.now());
    offer.setExpiresAt(Instant.now().plus(rule.getOfferValidityDays(), ChronoUnit.DAYS));
    offerRepository.save(offer);

    // Reset progress counters
    progress.setRideCount(0);
    progress.setTotalDistanceKm(BigDecimal.ZERO);
    progress.setLastResetAt(Instant.now());
    progressRepository.save(progress);

    // Publish Kafka event (notification-service sends push to rider)
    loyaltyEventPublisher.publishFreeRideEarned(new FreeRideOfferEarnedEvent(offer));
}
```

### Free Ride Offer Query (called by ride-service via internal API)

```java
public FreeRideOffer findApplicableOffer(UUID riderId, String countryCode,
        String vehicleType, BigDecimal estimatedDistanceKm) {
    return offerRepository
        .findFirstByRiderIdAndCountryCodeAndVehicleTypeAndStatusAndExpiresAtAfterOrderByEarnedAtAsc(
            riderId, countryCode, vehicleType, OfferStatus.AVAILABLE, Instant.now())
        .filter(offer -> estimatedDistanceKm.compareTo(offer.getMaxDistanceKm()) <= 0)
        .orElse(null);
}
```

### Offer Redemption (called by ride-service via internal API)

```java
@Transactional
public void redeemOffer(UUID offerId, UUID rideId) {
    FreeRideOffer offer = offerRepository.findById(offerId)
        .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
    if (offer.getStatus() != OfferStatus.AVAILABLE) {
        throw new ConflictException("Offer is not available for redemption");
    }
    if (offer.getExpiresAt().isBefore(Instant.now())) {
        offer.setStatus(OfferStatus.EXPIRED);
        offerRepository.save(offer);
        throw new BadRequestException("Offer has expired");
    }
    offer.setStatus(OfferStatus.REDEEMED);
    offer.setRedeemedAt(Instant.now());
    offer.setRideId(rideId);
    offerRepository.save(offer);
}
```

### Offer Expiry Scheduler

```java
@Scheduled(fixedDelay = 3_600_000)  // every hour
public void expireOffers() {
    List<FreeRideOffer> expired = offerRepository
        .findByStatusAndExpiresAtBefore(OfferStatus.AVAILABLE, Instant.now());
    expired.forEach(o -> o.setStatus(OfferStatus.EXPIRED));
    offerRepository.saveAll(expired);
}
```

---

## 7. API Endpoints

### Rider-Facing (requires authenticated rider)

Identity is read from gateway headers (`X-User-Id`, `X-User-Role`, `X-Country-Code`),
NOT from the request body.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/loyalty/progress` | Rider's progress per vehicle type |
| `GET` | `/api/v1/loyalty/offers` | Rider's available free ride offers |
| `GET` | `/api/v1/loyalty/rules` | Public -- show loyalty rules per country |

### Admin (requires `X-User-Role: ADMIN`)

| Method | Path | Description |
|---|---|---|
| `PUT` | `/api/v1/loyalty/rules/{id}` | Update loyalty rule thresholds |

### Internal (service-to-service, no gateway, no auth headers required)

| Method | Path | Description |
|---|---|---|
| `GET` | `/internal/loyalty/offers/applicable` | Find applicable offer for a ride |
| `POST` | `/internal/loyalty/offers/{id}/redeem` | Redeem an offer for a ride |

Internal endpoints are called directly by ride-service using RestClient. They bypass the
API gateway and are not exposed publicly.

### Internal Endpoint Details

**Find applicable offer:**
```
GET /internal/loyalty/offers/applicable?riderId={uuid}&countryCode={cc}&vehicleType={vt}&distanceKm={km}
```
Returns the best available offer matching the criteria, or 404 if none.

**Redeem offer:**
```
POST /internal/loyalty/offers/{id}/redeem?rideId={uuid}
```
Marks the offer as REDEEMED. Returns 409 if already redeemed, 400 if expired.

### Response Wrapper

Every endpoint returns `ApiResponse<T>` from common-lib:
```java
@GetMapping("/progress")
public ResponseEntity<ApiResponse<List<RiderProgressDto>>> getProgress(
        @RequestHeader("X-User-Id") UUID userId) {
    return ResponseEntity.ok(ApiResponse.ok(loyaltyService.getProgress(userId)));
}
```

---

## 8. Business Rules (CRITICAL -- Never Override)

1. **Free rides do NOT count toward the next offer** -- a completed free ride does not
   increment the rider's progress. Check `event.isFreeRide()` and return early.

2. **Free ride offers are vehicle-type-specific** -- an offer earned on Bajaj rides can
   only be redeemed on a Bajaj ride. Offers do not transfer across vehicle types.

3. **Free ride offers are distance-capped** -- if the rider's requested trip exceeds the
   offer's `maxDistanceKm`, the offer cannot be applied.

4. **Free rides are Twende's cost** -- when a free ride completes, payment-service credits
   the driver's wallet with the full fare. The driver is never penalised for accepting a
   free ride. This service does not handle payments -- ride-service and payment-service
   coordinate the wallet credit.

5. **Progress resets on award** -- when a free ride offer is earned, ride count and distance
   reset to zero. The rider starts accumulating fresh progress toward the next offer.

6. **One offer per threshold** -- each time the threshold is reached, exactly one offer is
   created. The rider must use or let it expire before a new one can be earned.

7. **Offers expire** -- offers have a validity period (`offerValidityDays`). Expired offers
   are marked `EXPIRED` by the hourly scheduler and cannot be redeemed.

8. **Money arithmetic uses BigDecimal only** -- never `double`, never `float`.

---

## 9. Application Configuration

```yaml
server:
  port: 8095

spring:
  application:
    name: loyalty-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_loyalty
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
    consumer:
      group-id: loyalty-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: tz.co.twende.*
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0

logging:
  level:
    tz.co.twende: DEBUG
    org.springframework.kafka: WARN
```

---

## 10. Authentication and Authorization

This service does NOT validate JWTs directly. The API gateway handles JWT validation and
forwards identity via headers:

| Header | Description |
|--------|-------------|
| `X-User-Id` | UUID of the authenticated user |
| `X-User-Role` | `RIDER`, `DRIVER`, or `ADMIN` |
| `X-Country-Code` | Two-letter country code (e.g., `TZ`) |

**Endpoint security rules:**
- `/api/v1/loyalty/progress` -- requires `X-User-Role: RIDER`
- `/api/v1/loyalty/offers` -- requires `X-User-Role: RIDER`
- `/api/v1/loyalty/rules` GET -- public
- `/api/v1/loyalty/rules/{id}` PUT -- requires `X-User-Role: ADMIN`
- `/internal/**` -- no auth headers; must only be reachable from internal network
- `/actuator/health` -- public

---

## 11. Conventions

**These apply to every file in this service. Never deviate.**

- All entities extend `BaseEntity` from common-lib (ULID-based UUID PK, createdAt, updatedAt, countryCode)
- Money fields use `BigDecimal` -- never `double` or `float`
- Timestamps use `Instant` -- never `LocalDateTime` or `Date`
- All controller methods return `ApiResponse<T>` from common-lib
- Validate all incoming requests with `@Valid @RequestBody`
- No cross-service repository access -- use RestClient to call other services' internal APIs
- No Feign, no Eureka, no Spring Cloud -- use Spring `RestClient` for inter-service calls
- All Kafka consumers are idempotent -- duplicate events must not create duplicate progress entries
- MapStruct for entity-to-DTO mapping
- Lombok for boilerplate reduction (`@Getter @Setter @NoArgsConstructor` on entities)

---

## 12. Testing

### Unit Tests

- `LoyaltyService.checkAndAwardOffer()` -- threshold met, threshold not met, no active rule
- `LoyaltyService.findApplicableOffer()` -- matching offer, expired offer, distance exceeds max, wrong vehicle type
- `OfferRedemptionService.redeemOffer()` -- successful redemption, already redeemed (conflict), expired offer
- `OfferExpiryScheduler` -- correctly expires stale offers, does not expire future offers
- Progress tracking -- free rides skipped, distance calculation, counter increment

### Integration Tests

- Use Testcontainers for PostgreSQL, Redis, and Kafka
- Test end-to-end: publish RideCompletedEvent -> progress updated -> threshold reached -> offer created -> Kafka event published
- Test offer redemption via internal API
- Test offer expiry scheduler
- Test idempotent event processing (same event twice does not double-count)

### Test Naming

```java
@Test
void givenRiderWith19Rides_whenRideCompleted_thenProgressIncrementedButNoOffer() { ... }

@Test
void givenRiderWith20BajajRidesAnd100Km_whenRideCompleted_thenFreeRideOfferAwarded() { ... }

@Test
void givenFreeRide_whenRideCompleted_thenProgressNotIncremented() { ... }

@Test
void givenAvailableOffer_whenRedeemed_thenStatusIsRedeemedAndRideIdSet() { ... }

@Test
void givenExpiredOffer_whenRedeemed_thenBadRequestThrown() { ... }

@Test
void givenBajajOffer_whenEconomyCarRideRequested_thenOfferNotApplicable() { ... }

@Test
void givenOfferWith5KmMax_whenRideIs8Km_thenOfferNotApplicable() { ... }
```

### Coverage

- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw verify`
- Report: `target/site/jacoco/index.html`
- Excluded from coverage: entities, DTOs, enums, config classes, `LoyaltyServiceApplication`

---

## Implementation Steps

- [ ] 1. `application.yml` -- port 8095, datasource `twende_loyalty`, Redis, Kafka (`consumer.group-id: loyalty-service-group`), JPA validate, Flyway enabled
- [ ] 2. Entities: `LoyaltyRule` (countryCode, vehicleType, requiredRides, requiredDistanceKm, freeRideMaxDistanceKm, offerValidityDays, isActive), `RiderProgress` (riderId, countryCode, vehicleType, rideCount, totalDistanceKm, lastResetAt), `FreeRideOffer` (riderId, countryCode, vehicleType, maxDistanceKm, status, earnedAt, expiresAt, redeemedAt, rideId) -- all extend `BaseEntity`
- [ ] 3. Repositories: `LoyaltyRuleRepository` (`findByCountryCodeAndVehicleTypeAndIsActiveTrue`), `RiderProgressRepository` (`findByRiderIdAndCountryCodeAndVehicleType`), `FreeRideOfferRepository` (`findFirstBy...OrderByEarnedAtAsc`, `findByStatusAndExpiresAtBefore`)
- [ ] 4. `LoyaltyService`: `onRideCompleted` (skip free rides, increment progress, check threshold, award if met), `findApplicableOffer(riderId, countryCode, vehicleType, distanceKm)`, `getProgress(riderId)`, `getRules(countryCode)`, `updateRule(id, request)` + `OfferRedemptionService`: `redeemOffer(offerId, rideId)` -- `@Transactional`, validate AVAILABLE + not expired
- [ ] 5. Kafka consumer: `LoyaltyEventConsumer` listening on `twende.rides.completed` (group: `loyalty-service-group`) -- skip if `freeRide=true`, increment progress, call `checkAndAwardOffer`, ensure idempotency
- [ ] 6. Kafka producer: `LoyaltyEventPublisher` publishing `FreeRideOfferEarnedEvent` to `twende.loyalty.free-ride-earned`
- [ ] 7. `OfferExpiryScheduler`: `@Scheduled(fixedDelay = 3_600_000)`, find AVAILABLE offers past `expiresAt`, mark EXPIRED
- [ ] 8. `LoyaltyController` (rider-facing: GET `/progress`, GET `/offers`, GET `/rules`) + admin PUT `/rules/{id}` + `LoyaltyInternalController` (GET `/internal/loyalty/offers/applicable`, POST `/internal/loyalty/offers/{id}/redeem`)
- [ ] 9. Flyway migrations: `V1__create_loyalty_schema.sql` (tables + indexes), `V2__seed_loyalty_rules.sql` (Tanzania rules for BAJAJ, BODA_BODA, ECONOMY_CAR)
- [ ] 10. MapStruct mapper: `LoyaltyMapper` for entity-to-DTO conversions
- [ ] 11. Dockerfile — Multi-stage build (eclipse-temurin:21-jdk-alpine for build, 21-jre-alpine for run). Non-root `twende` user. Health check on `/actuator/health`. Expose port 8095.
- [ ] 12. OpenAPI config — `OpenApiConfig.java` with SpringDoc `OpenAPI` bean. Title: "Loyalty Service API". Swagger UI at `/swagger-ui.html`.
- [ ] 13. Unit tests + integration tests (Testcontainers for PostgreSQL, Redis, Kafka), verify >= 80% coverage with `./mvnw verify`
