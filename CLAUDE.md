# CLAUDE.md — Twende Platform

> This file is the single source of truth for Claude Code when building Twende.
> Read it fully before writing any code. Each service also has its own `CLAUDE.md`
> with service-specific implementation details — read both this file and the
> relevant service file before working on any module.

---

## 1. What is Twende?

Twende ("Let's Go" in Swahili) is a **transport platform** for African markets, launching
first in **Tanzania**. It covers three service categories: **ride-hailing**, **charter
transport** (group/event bookings), and **cargo/freight**. The core competitive advantage
is flexible pricing — drivers choose between a **subscription model** (keep 100% of
earnings) or a **flat fee model** (Twende takes a percentage per trip).

### Service Categories

| Category | Description | Booking | Payment |
|----------|-------------|---------|---------|
| **Rides** | On-demand ride-hailing (Bajaj, Boda, Car) | On-demand (now) | Cash at end of trip |
| **Charter** | Pre-booked group transport for events (weddings, funerals, meetings) | Scheduled (future date/time), one-way or round trip | Cash — upfront before trip starts or upon completion |
| **Cargo** | Pre-booked freight transport | Scheduled (future date/time), one-way or round trip | Cash — upfront before trip starts or upon completion |

### Revenue Models

| Model | How it works | Available for |
|-------|-------------|---------------|
| **Subscription** | Driver pays daily/weekly/monthly bundle, keeps 100% of earnings | Rides only |
| **Flat fee** | Twende takes a configured percentage per trip from driver earnings | Rides, Charter, Cargo |

- Ride drivers **choose** subscription or flat fee (can switch monthly)
- Charter and Cargo drivers are **always flat fee** — no subscription option

### Key Differentiating Features (build these, do not simplify them away)

**Rides:**
- Driver subscription bundles per vehicle type:
  - Boda Boda: daily TSh 1,000 / weekly TSh 5,000 / monthly TSh 18,000
  - Bajaj: daily TSh 2,000 / weekly TSh 10,000 / monthly TSh 30,000
  - Economy Car: daily TSh 3,500 / weekly TSh 18,000 / monthly TSh 55,000
- Flat fee alternative (e.g. 15% per trip — configurable per country)
- Fare boost: rider can increase their offer to attract faster acceptance
- Rejection counter: rider sees how many drivers passed on their trip
- Trip start OTP: rider shares a 4-digit code with the driver to begin the trip
- Broadcast-and-accept matching: offer is sent to ALL nearby eligible drivers simultaneously

**Charter:**
- Pre-book minibuses and buses for specific dates/times
- Quality tiers: Standard vs Luxury (AC, reclining seats, WiFi)
- One-way or round trip support
- Drivers browse and accept available bookings (not broadcast matching)
- Cash payment — upfront before trip or upon job completion

**Cargo:**
- Pre-book cargo vehicles for specific dates/times
- Vehicle types by capacity: Cargo Tuk-tuk, Light Truck, Medium Truck, Heavy Truck
- Fixed pricing: baseFare + (distanceKm * perKm) + weightTierSurcharge — no time component
- Weight tiers (LIGHT, MEDIUM, FULL) instead of exact kg — customer selects tier at booking
- Customer knows exact price at booking time — price does not change regardless of loading duration
- Loading/unloading is customer's responsibility; driver can optionally help (arranged off-platform)
- One-way or round trip support
- Drivers browse and accept available bookings (not broadcast matching)
- Cash payment — upfront before trip or upon job completion
- Always flat fee — no subscription option

**All categories:**
- Multi-country config: Tanzania now, Kenya and Uganda without code changes later
- Trip start OTP for all categories

### Vehicle Types (Tanzania)

**Rides:** Bajaj (tuk-tuk), Boda Boda (motorcycle), Economy Car

**Charter:**

| Type | Passengers | Quality |
|------|-----------|---------|
| Minibus Standard | 14-18 | Basic |
| Minibus Luxury | 14-18 | AC, reclining seats, audio system |
| Bus Standard | 30-50 | Basic |
| Bus Luxury | 30-50 | AC, WiFi, reclining seats |

**Cargo:**

| Type | Capacity |
|------|----------|
| Cargo Tuk-tuk | Up to 500 kg |
| Light Truck | Up to 3 tonnes |
| Medium Truck | 3-10 tonnes |
| Heavy Truck | 10+ tonnes |

---

## 2. Architecture: Microservices Monorepo

**16 independent Spring Boot microservices** + 1 shared library, all in one Maven monorepo.
Each service owns its own PostgreSQL database (database-per-service pattern). No service
reads another service's database directly.

**Communication patterns:**
```
Synchronous  → REST over HTTP (Spring RestClient, direct URLs via env vars)
Asynchronous → Apache Kafka (ride lifecycle, payments, notifications)
Real-time    → WebSocket (live driver location, ride status updates)
Caching      → Redis (location data, country config, session tokens, rate limiting)
```

**Why microservices:**
- Independent scaling (matching-engine and location-service need different resources)
- Independent deployment (auth changes don't require ride-service redeployment)
- Team ownership (each service has clear domain boundaries)
- Technology flexibility (api-gateway uses WebFlux, all others use WebMvc)

**What we do NOT use (do not add these):**
- **No Eureka** — services use direct URLs via environment variables
- **No Config Server** — each service has its own `application.yml` with env var substitution
- **No OpenFeign** — use Spring `RestClient` for all sync inter-service calls
- **No Google Maps SDK** — call Google Maps REST APIs via `RestClient` directly
- **No Africa's Talking SDK** — call AT REST API via `RestClient` directly

### Service Inventory

| Service | Port | Database | Purpose |
|---|---|---|---|
| `api-gateway` | 8080 | — | Entry point, JWT validation, rate limiting, routing |
| `auth-service` | 8081 | `twende_auth` | OAuth2 Authorization Server, JWT issuance, OTP |
| `country-config-service` | 8082 | `twende_config` | Per-country config, vehicle types, feature flags |
| `user-service` | 8083 | `twende_users` | Rider profiles, preferences, saved places |
| `driver-service` | 8084 | `twende_drivers` | Driver profiles, documents, vehicle management |
| `ride-service` | 8085 | `twende_rides` | Ride lifecycle orchestration |
| `matching-service` | 8086 | `twende_matching` | Broadcast-and-accept driver matching |
| `location-service` | 8087 | `twende_locations` | WebSocket location tracking, geo queries, zones |
| `pricing-service` | 8088 | `twende_pricing` | Fare estimation and calculation, surge |
| `payment-service` | 8089 | `twende_payments` | Wallets, Selcom, cash reconciliation |
| `subscription-service` | 8090 | `twende_subscriptions` | Driver bundle management and billing |
| `notification-service` | 8091 | `twende_notifications` | Push (FCM), SMS (Africa's Talking), in-app, email |
| `rating-service` | 8092 | `twende_ratings` | Rider and driver ratings |
| `analytics-service` | 8093 | `twende_analytics` | Earnings dashboards, business metrics |
| `compliance-service` | 8094 | `twende_compliance` | SUMATRA reporting, audit logs |
| `loyalty-service` | 8095 | `twende_loyalty` | Rider loyalty programme, free rides |

**Shared library (not a service):**
- `common-lib` — Base entities, enums, Kafka event schemas, exceptions, utilities

### Request Flow: Rider Books a Ride

```
Rider App
  │
  ▼
API Gateway (8080)
  │  validates JWT, injects X-User-Id / X-User-Role / X-Country-Code headers
  ▼
Ride Service (8085)  ──── REST ────▶  Pricing Service (8088)
  │  creates ride record              returns fare estimate
  │
  │  publishes Kafka: twende.rides.requested
  ▼
Matching Service (8086)
  │  queries Redis GEO for nearby drivers
  │  broadcasts offer to closest 30 drivers via push notification
  │  first driver to ACCEPT wins (Redis SETNX atomic lock)
  │  publishes Kafka: twende.rides.offer-accepted
  ▼
Ride Service (8085)
  │  updates ride status → DRIVER_ASSIGNED
  │  publishes Kafka: twende.rides.status-updated
  ▼
Notification Service (8091)
     sends push to rider + driver
```

### Request Flow: Ride Completion

```
Ride Service (8085)
  │  publishes Kafka: twende.rides.completed
  │
  ├──▶ Payment Service (8089)     credits driver wallet (free ride: Twende pays)
  ├──▶ Rating Service (8092)      triggers rating prompt
  ├──▶ Analytics Service (8093)   records trip event
  ├──▶ Loyalty Service (8095)     updates rider progress, checks free ride threshold
  └──▶ Compliance Service (8094)  logs trip for SUMATRA
```

---

## 3. Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 (LTS) | Language |
| Spring Boot | 4.0.5 | Framework |
| Spring Cloud | 2025.0.0 | Gateway, circuit breaker |
| Spring Cloud Gateway | (included) | API Gateway (WebFlux-based) |
| Spring Security | (included with Boot 4) | Auth + method security |
| Spring Authorization Server | latest compatible | OAuth2 JWT issuance (auth-service only) |
| Spring Data JPA + Hibernate | (included) | ORM |
| Spring Data Redis | (included) | Caching, GEO, rate limiting |
| Spring Kafka | (included) | Async inter-service events |
| Spring WebSocket | (included) | Real-time location |
| PostgreSQL | 16 | Primary database (one per service) |
| PostGIS | (PostgreSQL extension) | Spatial queries in location-service |
| Flyway | (included with Boot) | Schema migrations (per-service) |
| Redis | 7 | Cache, GEO, sessions, rate limiting |
| Apache Kafka | 3.8 (KRaft mode) | Async event bus |
| Resilience4j | 2.2.0 | Circuit breaking (api-gateway) |
| Lombok | 1.18.34 | Boilerplate reduction |
| MapStruct | 1.6.0 | Entity <> DTO mapping |
| SpringDoc OpenAPI | 2.6.0 | API documentation |
| Micrometer + Prometheus | (included) | Metrics |
| Micrometer Tracing + Zipkin | (included) | Distributed tracing |
| Testcontainers | 1.20.0 | Integration tests |
| ULID Creator | 5.2.3 | Time-sortable unique IDs |
| Firebase Admin SDK | 9.4.2 | Push notifications (FCM) |
| MinIO SDK | 8.6.0 | File storage (driver docs) |
| SendGrid | 4.10.2 | Email |

**External APIs called via Spring `RestClient` (no SDK):**
- Google Maps REST API — geocoding, routing, ETA, autocomplete
- Africa's Talking REST API — SMS
- Selcom REST API — mobile money (Tanzania)
- OSRM — routing (Phase 2, self-hosted)
- Nominatim — geocoding (Phase 3, self-hosted)

---

## 4. Monorepo Structure

```
twende-platform/
├── pom.xml                          # Parent POM (dependency management, plugins)
├── CLAUDE.md                        # This file (architecture overview)
├── docker-compose.yml               # Local dev: Postgres, Redis, Kafka, Zipkin, MinIO
├── Makefile                         # Common build targets
├── .env.example                     # Environment variable template
├── .github/workflows/ci.yml         # CI/CD pipeline
├── infra/postgres/init-databases.sh # Creates per-service databases
├── common-lib/                      # Shared library (JAR, not executable)
│   └── CLAUDE.md
├── api-gateway/                     # Spring Cloud Gateway
│   └── CLAUDE.md
├── auth-service/                    # OAuth2 + OTP authentication
│   └── CLAUDE.md
├── country-config-service/          # Multi-country master config
│   └── CLAUDE.md
├── user-service/                    # Rider profiles
│   └── CLAUDE.md
├── driver-service/                  # Driver profiles, documents, vehicles
│   └── CLAUDE.md
├── location-service/                # WebSocket, Redis GEO, zones, geocoding
│   └── CLAUDE.md
├── pricing-service/                 # Fare calculation, surge
│   └── CLAUDE.md
├── matching-service/                # Broadcast-and-accept matching
│   └── CLAUDE.md
├── ride-service/                    # Ride lifecycle orchestration
│   └── CLAUDE.md
├── payment-service/                 # Wallets, Selcom, payouts
│   └── CLAUDE.md
├── subscription-service/            # Driver bundles
│   └── CLAUDE.md
├── notification-service/            # Push, SMS, email, templates
│   └── CLAUDE.md
├── loyalty-service/                 # Rider loyalty, free rides
│   └── CLAUDE.md
├── rating-service/                  # Rider/driver ratings
│   └── CLAUDE.md
├── analytics-service/               # Event ingestion, dashboards
│   └── CLAUDE.md
└── compliance-service/              # SUMATRA reporting
    └── CLAUDE.md
```

### Build Commands

```bash
# Build entire monorepo
./mvnw clean install

# Build a single service
./mvnw -pl auth-service -am clean package

# Run tests for one service
./mvnw -pl ride-service test

# Format code (Google Java Format, AOSP style)
./mvnw spotless:apply    # or: make format

# Start local infrastructure
make up    # starts Postgres, Redis, Kafka, Zipkin, MinIO
```

---

## 5. common-lib Contents

`common-lib` is a shared Java library (plain JAR, not executable) imported by all services.
It provides consistency across the platform. See `common-lib/CLAUDE.md` for full details.

### Base Entity (ULID-based primary keys)

ULIDs are time-sortable, globally unique, and stored as standard UUID columns in PostgreSQL.
The custom `@UlidId` annotation (backed by `UlidGenerator` implementing Hibernate's
`BeforeExecutionGenerator`) produces monotonically increasing IDs for better B-tree index
performance. Pre-set IDs (e.g. from Kafka events) are preserved automatically.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
public abstract class BaseEntity {
    @Id
    @UlidId
    @Column(updatable = false, nullable = false)
    private UUID id;

    @CreatedDate @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 2)
    private String countryCode;
}
```

### Standard Response Wrappers

```java
// Every controller method returns ApiResponse<T>
public static <T> ApiResponse<T> ok(T data) { ... }
public static <T> ApiResponse<T> error(String message) { ... }
```

### Enums

`RideStatus`, `DriverStatus`, `VehicleType`, `PaymentStatus`, `PaymentMethod`,
`SubscriptionPlan`, `SubscriptionStatus`, `NotificationType`, `DocumentType`,
`CountryCode`, `UserRole`, `DriverOfferAction`, `OfferStatus`

### Kafka Events (base class + all event POJOs)

```java
public abstract class KafkaEvent {
    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private String countryCode;
    private Instant timestamp = Instant.now();
    private String correlationId;  // trace ID for distributed tracing
}
```

Events: `RideRequestedEvent`, `RideStatusUpdatedEvent`, `RideCompletedEvent`,
`RideFareBoostedEvent`, `DriverMatchedEvent`, `DriverStatusUpdatedEvent`,
`DriverRejectedRideEvent`, `DriverOfferNotificationEvent`, `RideOfferAcceptedEvent`,
`PaymentInitiatedEvent`, `PaymentCompletedEvent`, `SubscriptionActivatedEvent`,
`SubscriptionExpiredEvent`, `UserRegisteredEvent`, `SendNotificationEvent`,
`FreeRideOfferEarnedEvent`, `FreeRideCompletedEvent`

### Exception Hierarchy

`TwendeException` (base) -> `ResourceNotFoundException`, `UnauthorizedException`,
`ConflictException`, `BadRequestException`. Plus `GlobalExceptionHandler`
(`@RestControllerAdvice`, auto-configured).

### Utilities

- `PhoneUtil` — E.164 normalisation
- `CurrencyUtil` — format amounts per country
- `OtpUtil` — 4-digit and 6-digit OTP generation (SecureRandom)
- `PaginationUtil` — page request helpers

---

## 6. Global Conventions

**These apply to every service. Never deviate.**

### Entities
```java
@Entity
@Table(name = "rides")
@Getter @Setter @NoArgsConstructor
public class Ride extends BaseEntity {
    // All entities extend BaseEntity
    // BaseEntity provides: UUID id (ULID-generated), Instant createdAt, Instant updatedAt, String countryCode
}
```

### API Response wrapper — ALWAYS use this
```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<RideDto>> getRide(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(rideService.getRide(id)));
}
```

### Money — NEVER use double or float
```java
private BigDecimal amount;  // Java: BigDecimal
// DB: NUMERIC(12,2)
// WRONG: double amount; float amount;
```

### Timestamps — always UTC, always Instant
```java
private Instant createdAt;     // correct
// WRONG: LocalDateTime, Date
```

### Reading the calling user — from gateway-injected headers
The API Gateway validates the JWT and injects user context as HTTP headers.
Downstream services read these headers — they do NOT re-validate the JWT.

```java
@GetMapping("/me")
public ResponseEntity<ApiResponse<UserDto>> getProfile(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestHeader("X-User-Role") String role,
        @RequestHeader("X-Country-Code") String countryCode) {
    return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
}

// Or use a shared helper component:
@Component
public class RequestContext {
    public UUID userId(HttpServletRequest request) {
        return UUID.fromString(request.getHeader("X-User-Id"));
    }
    public String role(HttpServletRequest request) {
        return request.getHeader("X-User-Role");
    }
    public String countryCode(HttpServletRequest request) {
        return request.getHeader("X-Country-Code");
    }
}
```

### Validation — always validate incoming requests
```java
@PostMapping
public ResponseEntity<ApiResponse<...>> create(@Valid @RequestBody CreateRideRequest req) { ... }

public class CreateRideRequest {
    @NotNull private VehicleType vehicleType;
    @NotNull @DecimalMin("0.0") private BigDecimal pickupLat;
}
```

### Module boundary rule — services never share databases
```java
// CORRECT: ride-service calls pricing-service via REST
@Component
public class PricingClient {
    private final RestClient restClient;

    public PricingClient(@Value("${twende.services.pricing-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public FareEstimate estimate(EstimateRequest request) {
        return restClient.post()
            .uri("/internal/pricing/estimate")
            .body(request)
            .retrieve()
            .body(FareEstimate.class);
    }
}

// WRONG: ride-service importing pricing-service's repository
```

### Kafka events for async cross-service communication
```java
// Publishing an event
kafkaTemplate.send("twende.rides.completed",
    ride.getCountryCode() + ":" + ride.getId(),  // key for partition locality
    new RideCompletedEvent(ride));

// Consuming in another service
@KafkaListener(topics = "twende.rides.completed", groupId = "${spring.application.name}")
public void onRideCompleted(RideCompletedEvent event) {
    complianceService.logTrip(event);
}
```

### Database conventions
```sql
id           UUID         PRIMARY KEY  -- ULID generated by application
country_code CHAR(2)      NOT NULL
created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
amount       NUMERIC(12,2) NOT NULL    -- Money: always NUMERIC, never FLOAT
status       VARCHAR(30)  NOT NULL     -- Enums: VARCHAR with CHECK or PG enum
```

### Flyway migrations — per service
Each service has its own `src/main/resources/db/migration/` directory.
```
V1__{description}.sql
V2__{description}.sql
```
`ddl-auto: validate` — Flyway manages schema, Hibernate only validates.

---

## 7. Security Architecture

### Overview

```
External client → API Gateway → validates JWT (JWKS from auth-service) → injects headers → route to service
Internal service → service → direct REST call (bypasses gateway), trusted internal network
```

### auth-service (OAuth2 Authorization Server)
- Issues JWTs signed with RSA key pair
- Exposes JWKS at `/oauth2/jwks`
- OTP-based login: phone number + 6-digit SMS OTP
- Token claims: `sub` (userId UUID), `roles` (list), `countryCode`, `scope`
- Refresh token rotation enabled
- Token blocklist via Redis (on logout)

### API Gateway (JWT validator + header injector)
- Validates JWT against auth-service JWKS on every request
- Injects headers: `X-User-Id`, `X-User-Role`, `X-Country-Code`
- Rate limiting: per-IP and per-user via Redis
- WebSocket routes (`/ws/**`) bypass auth filter (auth on handshake instead)
- CORS configured for client apps

### Downstream services (trust gateway headers)
- Do NOT include `spring-boot-starter-oauth2-resource-server`
- Read `X-User-Id`, `X-User-Role`, `X-Country-Code` from request headers
- Use `@PreAuthorize` equivalents by checking `X-User-Role` header
- Admin endpoints check `X-User-Role == "ADMIN"`

### Endpoint security rules
- `/api/v1/auth/**` and `/oauth2/**` → public (no JWT required)
- `/api/v1/config/**` GET → public; PUT/POST/PATCH → ADMIN only
- `/ws/**` → auth via `?token=` query parameter on WebSocket handshake
- `/actuator/health` → public
- `/internal/**` → service-to-service only (not routed through gateway)
- Everything else → authenticated (JWT required via gateway)

---

## 8. Kafka Event Architecture

### Topic naming convention
```
twende.{domain}.{event-name}
```

### Key format
```
{countryCode}:{entityId}
```
This ensures all events for one ride land on the same Kafka partition, maintaining ordering.

### Serialisation
JSON with type headers (`JsonSerializer` / `JsonDeserializer`). All events extend `KafkaEvent`
from `common-lib`.

### Consumer groups
Each service uses its own name as the consumer group ID:
`spring.kafka.consumer.group-id: ${spring.application.name}`

### Topic Registry

| Topic | Producer | Consumers | Trigger |
|---|---|---|---|
| `twende.rides.requested` | ride-service | matching-service | Ride created |
| `twende.rides.status-updated` | ride-service | notification-service | Any status change |
| `twende.rides.completed` | ride-service | payment, rating, analytics, compliance, loyalty | Ride completed |
| `twende.rides.cancelled` | ride-service | notification-service, analytics | Ride cancelled |
| `twende.rides.fare-boosted` | ride-service | matching-service | Rider boosts fare |
| `twende.rides.offer-accepted` | matching-service | ride-service | Driver wins acceptance race |
| `twende.drivers.matched` | matching-service | ride-service, notification-service | Legacy (use offer-accepted) |
| `twende.drivers.status-updated` | driver-service | location-service | Driver goes online/offline |
| `twende.drivers.rejected-ride` | matching-service | ride-service | Driver explicitly rejects offer |
| `twende.drivers.offer-notification` | matching-service | notification-service | Push offer to driver |
| `twende.drivers.approved` | driver-service | notification-service | Admin approves driver |
| `twende.payments.completed` | payment-service | notification-service, analytics | Payment processed |
| `twende.payments.failed` | payment-service | notification-service | Payment failed |
| `twende.subscriptions.activated` | subscription-service | driver-service, notification-service | Bundle purchased |
| `twende.subscriptions.expired` | subscription-service | driver-service, notification-service | Bundle expired |
| `twende.users.registered` | auth-service | user-service, driver-service | New user registered |
| `twende.notifications.send` | any service | notification-service | Direct notification request |
| `twende.loyalty.free-ride-earned` | loyalty-service | notification-service | Free ride offer awarded |
| `twende.config.country-updated` | country-config-service | all services with cached config | Config changed |
| `twende.ratings.submitted` | rating-service | analytics-service | Rating submitted |

---

## 9. Data Architecture

### Database-per-service

Each service has its own PostgreSQL database. The `infra/postgres/init-databases.sh` script
creates all databases for local development.

### Redis usage

| Purpose | Key pattern | TTL | Used by |
|---|---|---|---|
| Driver live positions | `drivers:{countryCode}:{vehicleType}` | — (GEO set) | location-service |
| Driver location detail | `driver:location:{driverId}` | 90s | location-service |
| Trip trace (in-progress) | `ride:trace:{rideId}` | 48h | location-service |
| Country config cache | `country-config:{code}` | 5 min | country-config-service |
| OTP rate limiting | `otp:rate:{phoneNumber}` | 10 min | auth-service |
| Token blocklist | `token:blocked:{jti}` | remaining TTL | auth-service |
| Ride acceptance lock | `ride_accepted:{rideId}` | 60s | matching-service |
| Driver offer dedup | `driver_offered:{driverId}:{rideId}` | 20s | matching-service |
| Offered drivers set | `rides_offered_to:{rideId}` | 300s | matching-service |
| Driver rejection set | `driver_rejected:{rideId}` | 300s | matching-service |
| Surge multiplier | `surge:{countryCode}:{vehicleType}` | 60s | pricing-service |
| Rating aggregate | `rating:driver:{driverId}` | 1h | rating-service |
| API rate limits | `ratelimit:{ip}` / `ratelimit:{userId}` | 1s | api-gateway |
| Distance/direction cache | `route:{coordsHash}` | 1h | location-service |

---

## 10. Inter-Service Communication

### Synchronous: Spring RestClient

Services call each other's `/internal/**` endpoints directly using Spring `RestClient`.
Base URLs come from environment variables (Docker Compose service names or K8s DNS).

```java
@Component
public class DriverClient {
    private final RestClient restClient;

    public DriverClient(@Value("${twende.services.driver-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public DriverDto getDriver(UUID driverId) {
        return restClient.get()
            .uri("/internal/drivers/{id}", driverId)
            .retrieve()
            .body(DriverDto.class);
    }
}
```

### Asynchronous: Kafka

All significant state changes publish Kafka events. Other services consume them
independently. See section 8 for the topic registry.

### Real-time: WebSocket

`location-service` maintains WebSocket connections for live driver location streaming.
Endpoint: `ws://host/ws/location?token={jwt}` — JWT validated during handshake.

---

## 11. Important Business Rules (Never Override These)

1. **Subscription drivers keep 100%** — for drivers on the subscription model, ride fare
   goes entirely to the driver. Twende earns from subscription bundles only.

2. **Flat fee drivers pay a percentage** — for drivers on the flat fee model, Twende
   deducts a configured percentage (e.g. 15%) from the fare. The percentage is set per
   country and per service category in country-config-service.

3. **Charter and Cargo are always flat fee** — no subscription option for charter or cargo
   drivers. Twende always takes its cut.

4. **No subscription and no flat fee = no online** — a ride driver must have either an
   active subscription OR be registered for flat fee. Charter/cargo drivers must be
   registered for flat fee. Hard block in driver-service.

5. **Fare can only go up** — a rider can boost the fare during `REQUESTED` status but
   cannot reduce it. Once boosted, base fare + boost is the floor.

6. **OTP is single-use** — null out `tripStartOtpHash` immediately after successful
   verification. Never re-use the same OTP.

7. **Money arithmetic uses BigDecimal only** — never `double`, never `float`.

8. **Trip start requires physical presence** — OTP must be entered by the driver after
   arriving. Do not allow drivers to mark `IN_PROGRESS` without a valid OTP. No exceptions.

9. **Country config is read-only at runtime** — only admin endpoints can modify country
   config. Services read it (with caching) but never write it.

10. **Driver rejection is permanent for that ride** — once a driver rejects a ride,
   they must not receive another offer for the same ride, even if the fare is boosted.
   Check `driver_rejected:{rideId}` Redis set before re-broadcasting.

11. **Phone numbers in E.164 format** — always normalise via `PhoneUtil.normalise()` before
   storing. `+255712345678`, not `0712345678` or `255712345678`.

12. **Wallet updates are always transactional** — balance update and wallet_entry insert
    in one `@Transactional` method. Never update the balance without an entry.

13. **All customer payments are cash** — rides: cash at end of trip. Charter and cargo:
    cash either upfront before trip starts or upon job completion (customer and driver
    agree). No digital payment on the customer side. Selcom is only for driver
    subscriptions and wallet payouts.

14. **Free rides are Twende's cost** — when a loyalty free ride completes, Twende
    credits the driver's wallet with the full calculated fare. The driver is never
    penalised for accepting a free ride.

15. **Free ride offers are vehicle-type-specific** — an offer earned on Bajaj rides
    can only be redeemed on a Bajaj ride. Offers do not transfer across vehicle types.

16. **Free ride offers are distance-capped** — a free ride offer specifies a maximum
    distance (km). If the rider's requested trip exceeds this distance, the offer
    cannot be applied to that ride.

17. **Free rides don't count toward the next offer** — a completed free ride does not
    increment the rider's progress toward earning the next free ride offer.

18. **Rides cannot start in RESTRICTED zones** — if the pickup point falls inside a zone
    with type `RESTRICTED`, reject the ride request. No exceptions.

19. **Zone checks use PostGIS, not application code** — all point-in-polygon checks must
    use `ST_Covers` via native queries in `ZoneRepository`. Never iterate polygons in Java.

20. **Provider switching is per-city, not global** — each `OperatingCity` has its own
    `geocoding_provider`, `routing_provider`, and `autocomplete_provider` columns.
    Changing a provider for one city must not affect other cities.

21. **Google Maps API key is never exposed to clients** — all mapping API calls are
    server-side via the location-service. Frontend gets results from our endpoints only.

22. **SMS and push provider switching is per-country** — `CountryConfig.smsProvider` and
    `CountryConfig.pushProvider` determine which implementation handles notifications.
    Changing a provider for one country must not affect other countries.

23. **No services call SMS/push providers directly** — all notification sending goes through
    `NotificationService` in notification-service. Other services publish Kafka events only.

---

## 12. Mandatory Pre-Push Checks

**No code may be committed or pushed unless ALL checks below pass locally.**

If any step fails: STOP immediately, fix the issue, re-run the checks, and only proceed
when everything passes. These checks mirror the CI pipeline — passing them locally prevents
wasted CI cycles and ensures security issues are caught before code leaves the developer's
machine.

### Step 1: Code Formatting
```bash
./mvnw spotless:check
```
If it fails, auto-fix with `./mvnw spotless:apply` and re-check.

### Step 2: Build, Tests & Coverage (ALL services, not just the one you changed)
```bash
./mvnw clean verify
```
**This runs the ENTIRE monorepo** — all 17 modules, all tests, all coverage checks.
Do NOT use `-pl {service}` for pre-push checks. A change in common-lib or a shared
event class can break other services. Always verify the full build.

Requirements:
- All tests across ALL services MUST pass (zero failures, zero errors)
- No skipped or ignored tests unless justified
- JaCoCo coverage >= 80% line coverage on all non-excluded classes in every service

### Step 3: Dependency & Secret Vulnerability Scan
```bash
trivy fs --scanners vuln,secret --severity HIGH,CRITICAL .
```
Requirements:
- **ZERO** HIGH or CRITICAL vulnerabilities with available fixes
- **ZERO** exposed secrets (API keys, tokens, passwords, private keys)
- If a vulnerability has no fix available (`--ignore-unfixed`), document it and proceed

### Quick Reference
```bash
# Run all 3 checks in sequence (copy-paste this before every push):
./mvnw spotless:check && ./mvnw clean verify && trivy fs --scanners vuln,secret --severity HIGH,CRITICAL .
```

Or use the Makefile:
```bash
make check   # runs all 3 checks
```

---

## 13. Build Phases

Work through these phases in order. Do not start Phase N+1 until Phase N is complete and
all tests are passing.

**Phase completion rule — applies to EVERY phase:**
1. Implement all items listed for the phase
2. Write unit tests and integration tests covering all new code
3. **Run ALL pre-push checks from Section 12** — this means `./mvnw clean verify`
   across the ENTIRE monorepo (all services), not just the service you changed.
   A change in common-lib or shared events can break other services.
4. **Minimum 80% line coverage** on all new code. If below 80%, write more tests.
5. Fix any vulnerabilities or secrets found by Trivy before committing.
6. Once ALL checks pass across ALL services, commit and push.

**Detailed implementation sub-steps are in each service's `CLAUDE.md` file.** The phases
below define the order and what services to build. For the step-by-step breakdown of HOW
to build each service, refer to the **"Implementation Steps"** section in that service's
`{service}/CLAUDE.md`.

### Phase 1 — Foundation ✅
- [x] `common-lib` — see `common-lib/CLAUDE.md` → Implementation Steps
- [x] `auth-service` — see `auth-service/CLAUDE.md` → Implementation Steps
- [x] `api-gateway` — see `api-gateway/CLAUDE.md` → Implementation Steps

### Phase 2 — Core Data + Social Login ✅
- [x] `auth-service` (enhancement) — add Google and Apple social login as alternative to OTP.
      New `authProvider` field (PHONE, GOOGLE, APPLE) on AuthUser. Spring Security OAuth2
      Client for Google + Sign in with Apple (OIDC). Auto-populate fullName, email,
      profilePhotoUrl from social profile. Phone number prompt after social login if missing.
      Account linking by email. `UserRegisteredEvent` extended with profilePhotoUrl + email.
- [x] `country-config-service` — see `country-config-service/CLAUDE.md` → Implementation Steps
- [x] `user-service` — see `user-service/CLAUDE.md` → Implementation Steps
- [x] `driver-service` — see `driver-service/CLAUDE.md` → Implementation Steps

### Phase 3 — Ride Flow ✅
- [x] `location-service` — see `location-service/CLAUDE.md` → Implementation Steps
- [x] `pricing-service` — see `pricing-service/CLAUDE.md` → Implementation Steps
- [x] `matching-service` — see `matching-service/CLAUDE.md` → Implementation Steps
- [x] `ride-service` — see `ride-service/CLAUDE.md` → Implementation Steps
- [x] `user-service` (enhancement) — destination suggestions: frequent destinations
      (precomputed from ride history, top 3 per city) + recent rides by region.
      New table `user_destination_stats`, Kafka consumer for `RideCompletedEvent`,
      new endpoint `GET /api/v1/users/me/suggestions?lat=&lng=`.
- [x] `ride-service` (enhancement) — internal endpoint `GET /internal/rides/history`
      for user-service to fetch recent rides by userId + cityId.

### Phase 4 — Commerce ✅
- [x] `payment-service` — see `payment-service/CLAUDE.md` → Implementation Steps
- [x] `subscription-service` — see `subscription-service/CLAUDE.md` → Implementation Steps
- [x] `loyalty-service` — see `loyalty-service/CLAUDE.md` → Implementation Steps

### Phase 5 — Supporting Features ✅
- [x] `notification-service` — see `notification-service/CLAUDE.md` → Implementation Steps
- [x] `rating-service` — see `rating-service/CLAUDE.md` → Implementation Steps

### Phase 6 — Admin & Observability
- [ ] `analytics-service` — see `analytics-service/CLAUDE.md` → Implementation Steps
- [ ] `compliance-service` — see `compliance-service/CLAUDE.md` → Implementation Steps
- [ ] Admin endpoints across all services (`X-User-Role == ADMIN` check)
- [ ] Prometheus metrics exposed at `/actuator/prometheus`
- [ ] Zipkin tracing configured

### Phase 7 — Flat Fee Revenue Model ✅
- [x] `common-lib` — add enums: `ServiceCategory` (RIDE, CHARTER, CARGO), `RevenueModel`
      (SUBSCRIPTION, FLAT_FEE), `BookingType` (ON_DEMAND, SCHEDULED), `QualityTier`
      (STANDARD, LUXURY), `TripDirection` (ONE_WAY, ROUND_TRIP). Expand `VehicleType` enum
      with charter and cargo types. Add new Kafka events.
- [x] `country-config-service` — flat fee percentage config per country per service category,
      new vehicle type configs for charter/cargo with quality tiers
- [x] `subscription-service` — support flat fee as alternative to subscription. Driver
      chooses revenue model. Ride drivers can switch monthly.
- [x] `driver-service` — `revenueModel` field on driver profile, service category registration,
      vehicle quality tier. Go-online validation: subscription OR flat fee required.
- [x] `payment-service` — flat fee deduction from driver wallet on trip completion for
      flat-fee drivers. Calculate Twende's cut = fare * flatFeePercentage.

### Phase 8 — Charter Transport
- [ ] `ride-service` — add `serviceCategory`, `bookingType`, `scheduledPickupAt`,
      `tripDirection`, `qualityTier`, `returnPickupAt` fields. Support scheduled bookings.
- [ ] `pricing-service` — charter pricing: base + distance + duration + vehicle class +
      quality tier. Round trip = 2x distance pricing with discount.
- [ ] `matching-service` — scheduled matching mode: drivers browse available charter bookings
      and accept (marketplace model, not broadcast). Filter by vehicle type + quality tier.
- [ ] Charter vehicle type seed data for Tanzania (minibus standard/luxury, bus standard/luxury)
- [ ] Notification templates for booking confirmations, reminders, driver acceptance

### Phase 9 — Cargo Transport
- [ ] `ride-service` — add `cargoDescription`, `estimatedWeightKg`, `estimatedVolumeM3` fields
      for cargo bookings.
- [ ] `pricing-service` — cargo pricing: base + distance + weight tier + vehicle class.
- [ ] `matching-service` — cargo matching: same marketplace model as charter.
- [ ] Cargo vehicle type seed data for Tanzania (cargo tuk-tuk, light/medium/heavy truck)
- [ ] Notification templates for cargo booking confirmations

---

## 14. Testing Strategy

### Unit tests — for pure logic
- Use `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks`
- All `util/` classes, services, controllers
- State machine transitions, validation logic, edge cases

### Integration tests — Spring Boot 4 + Spring Cloud 2025.1.1 (Oakwood)

**Critical version requirements:**
- **Spring Boot 4.0.5** with **Spring Cloud 2025.1.1** (Oakwood). Do NOT use Spring Cloud
  2025.0.x (Northfields) — it targets Boot 3.5.x and causes `ClassNotFoundException` errors.
- **Spring Cloud Gateway 5.0.x** (ships with Oakwood). The gateway starter is
  `spring-cloud-starter-gateway-server-webflux` (not `spring-cloud-starter-gateway`).
  Route config prefix: `spring.cloud.gateway.server.webflux`.

### Spring Boot 4 Testing — DO / DO NOT (read this FIRST)

**These rules apply to EVERY test. Violations cause compilation or context failures.**

#### DO NOT (these will break):
- `import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` — **WRONG PACKAGE**
- `import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` — **WRONG PACKAGE**
- `import org.springframework.boot.test.web.client.TestRestTemplate` — **WRONG PACKAGE**
- `import org.springframework.test.bean.override.mockito.MockitoBean` — **WRONG PACKAGE**
- `new UserRegisteredEvent(uuid, name, phone, role)` — **WRONG CONSTRUCTOR** (7 fields now)
- `new GenericJackson2JsonRedisSerializer()` — **DEPRECATED**, use `GenericJacksonJsonRedisSerializer`
- `com.fasterxml.jackson.databind.ObjectMapper` for injected beans — Boot 4 auto-configures **Jackson 3** (`tools.jackson.databind.ObjectMapper`)

#### DO (correct patterns):
- `@MockitoBean` → `import org.springframework.test.context.bean.override.mockito.MockitoBean`
- `@WebMvcTest` → add `spring-boot-starter-webmvc-test` dependency, import from `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
- **OR** skip `@WebMvcTest` entirely and use plain `@ExtendWith(MockitoExtension.class)` with `@Mock` + `@InjectMocks` (recommended — simpler, no context loading)
- `WebTestClient` → add `spring-webflux` as test dependency, construct manually: `WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()`
- `docker-java.properties` → `api.version=1.44` (required for Docker 29 + Testcontainers 1.x)
- `application-test.yml` → `ddl-auto: create-drop`, `flyway.enabled: false`, dummy `kafka.bootstrap-servers`
- Event constructors → use **setters** (Lombok `@NoArgsConstructor` + `@Data`), not `@AllArgsConstructor`
- Pre-set ID entities → use `JdbcTemplate` in integration tests to insert test data (bypasses `@GeneratedValue` conflict), or `EntityManager.merge()` in production code
- Jackson 3 → `tools.jackson.databind.ObjectMapper` / `tools.jackson.core.JacksonException`
- Redis serializer → `GenericJacksonJsonRedisSerializer` (Jackson 3 based, from `org.springframework.data.redis.serializer`)

#### Test dependency checklist (add to pom.xml for EVERY service):
```xml
<!-- For WebTestClient (integration tests) -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webflux</artifactId>
    <scope>test</scope>
</dependency>

<!-- For @WebMvcTest (controller slice tests) — optional, can use Mockito instead -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Pattern: Unit test (services and controllers — NO Spring context)
```java
@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock private DriverProfileRepository driverRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private DriverService driverService;

    @Test
    void givenApprovedDriver_whenGoOnline_thenStatusUpdated() {
        // Arrange
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = new DriverProfile();
        driver.setId(driverId);
        driver.setStatus(DriverStatus.APPROVED);
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        // Act
        driverService.goOnline(driverId);

        // Assert
        assertEquals(DriverStatus.ONLINE_AVAILABLE, driver.getStatus());
        verify(driverRepository).save(driver);
    }
}
```
**Key rules for unit tests:**
- Use `@ExtendWith(MockitoExtension.class)`, NOT `@SpringBootTest`
- Mock all dependencies with `@Mock`, inject with `@InjectMocks`
- No Spring context loading — fast, no Docker needed
- For controller tests: call controller methods directly, assert on `ResponseEntity`
- Given_When_Then naming convention

### Pattern: Standard service integration test (with database + Redis)
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class ServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean  // org.springframework.test.context.bean.override.mockito.MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port).build();
    }
}
```

### Pattern: API Gateway integration test (no database, mock JWT decoder)
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;  // mock JWT validation

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port).build();
        when(jwtDecoder.decode(anyString()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
    }
}
```
**Gateway test notes:**
- Exclude Redis auto-config in test `application.yml` (rate limiter not needed in tests)
- Use `allow-bean-definition-overriding: true` in test config
- Routes point to `http://localhost:19999` (no downstream running) — tests verify filter
  behavior (401/403/404), not routing
- `@MockitoBean ReactiveJwtDecoder` replaces the real JWKS-based decoder but does NOT
  propagate into `AuthFilter` for valid-token tests (constructor injection limitation).
  Test valid JWT flow via unit tests instead.

### Required test resource files

**`src/test/resources/application-test.yml`** (for services with database):
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop    # Auto-create schema from entities in tests
  flyway:
    enabled: false             # Disable Flyway in tests (entities define schema)
  kafka:
    bootstrap-servers: localhost:9092  # Dummy, mocked via @MockitoBean
```

**`src/test/resources/docker-java.properties`** (for all services using Testcontainers):
```properties
api.version=1.44
```

### Test naming convention
```java
@Test
void givenRideInRequestedStatus_whenRiderBoostsFare_thenFareUpdatedAndRebroadcastTriggered() { ... }
```

### Coverage enforcement
- JaCoCo plugin with 80% minimum line coverage
- `./mvnw -pl {service} verify` fails if below 80%
- Excluded from coverage: entities, DTOs, enums, config classes, `*Application.class`

---

## 15. CI/CD Pipeline

GitHub Actions at `.github/workflows/ci.yml`. Runs on push to `main`/`develop` and PRs.

| Stage | What | Blocks? |
|---|---|---|
| Lint & Format | `spotless:check` (Google Java Format, AOSP) | Yes |
| Build & Test | `./mvnw clean install` with Postgres + Redis services | Yes |
| Security Scan | Trivy filesystem + secrets scan | Yes |
| CodeQL SAST | Static analysis (disabled until source code exists) | Yes |
| Container Scan | Trivy image scan (push only, planned) | Yes |
| Publish | Push to GHCR (main branch only, planned) | — |

**Formatting:** Run `./mvnw spotless:apply` or `make format` before committing.

---

## 16. External Integrations

| Integration | Provider | Used by | Pattern |
|---|---|---|---|
| Mobile money (TZ) | Selcom API | payment-service | RestClient, no SDK |
| SMS | Africa's Talking | notification-service | RestClient, no SDK |
| Push notifications | Firebase (FCM) | notification-service | Firebase Admin SDK |
| Maps & routing | Google Maps Platform | location-service | RestClient, no SDK |
| Object storage | MinIO (self-hosted S3) | driver-service | MinIO SDK |
| Email | SendGrid | notification-service | SendGrid SDK |
| Geocoding (Phase 3) | Nominatim | location-service | RestClient |
| Routing (Phase 2) | OSRM | location-service | RestClient |
| Regulatory (TZ) | SUMATRA | compliance-service | RestClient (adapter pattern) |

---

## 17. Application Configuration Template

Every service follows this standard `application.yml` structure:

```yaml
server:
  port: {port}

spring:
  application:
    name: {service-name}
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:{service_db}}
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
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
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
    consumer:
      group-id: ${spring.application.name}
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: tz.co.twende.common.events.*
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

# Inter-service URLs (override via env vars in deployment)
twende:
  services:
    auth-url: ${AUTH_SERVICE_URL:http://localhost:8081}
    config-url: ${CONFIG_SERVICE_URL:http://localhost:8082}
    user-url: ${USER_SERVICE_URL:http://localhost:8083}
    driver-url: ${DRIVER_SERVICE_URL:http://localhost:8084}
    ride-url: ${RIDE_SERVICE_URL:http://localhost:8085}
    matching-url: ${MATCHING_SERVICE_URL:http://localhost:8086}
    location-url: ${LOCATION_SERVICE_URL:http://localhost:8087}
    pricing-url: ${PRICING_SERVICE_URL:http://localhost:8088}
    payment-url: ${PAYMENT_SERVICE_URL:http://localhost:8089}
    subscription-url: ${SUBSCRIPTION_SERVICE_URL:http://localhost:8090}
    notification-url: ${NOTIFICATION_SERVICE_URL:http://localhost:8091}
```

---

## 18. Dockerfile Standard (All Services)

Every service (except `common-lib`) must have a `Dockerfile` in its module root.

### Standard Dockerfile (services that depend on common-lib)

**CRITICAL — these 3 things are required and frequently missed:**
1. **`RUN apk add --no-cache curl`** in the build stage — the Maven wrapper (`mvnw`)
   uses `curl` to download Maven. Without it, the build fails with `curl: not found`.
2. **Stub POMs for sibling modules** — the parent `pom.xml` references all 16 modules.
   When building a single service in Docker, the other modules don't exist, so Maven
   fails to resolve them. Create minimal stub `pom.xml` files for every sibling module.
3. **`./mvnw -N install`** — installs the parent POM into the local Maven repo before
   building child modules. Without it, child modules can't find their parent.

**Do NOT use `COPY .mvn .mvn`** — the `.mvn` directory does not exist in this project.
The `mvnw` wrapper script downloads Maven on its own using `curl`.

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache curl
WORKDIR /workspace

# Copy parent pom, common-lib (real dependency), and the service
COPY pom.xml .
COPY common-lib common-lib
COPY {service-name} {service-name}

# Create minimal pom stubs for sibling modules referenced in parent pom
RUN for mod in api-gateway auth-service country-config-service user-service driver-service \
    location-service pricing-service matching-service ride-service payment-service \
    subscription-service notification-service loyalty-service rating-service \
    analytics-service compliance-service; do \
    mkdir -p "$mod"; \
    echo '<project><modelVersion>4.0.0</modelVersion><parent><groupId>tz.co.twende</groupId><artifactId>twende-parent</artifactId><version>1.0.0-SNAPSHOT</version></parent><artifactId>'"$mod"'</artifactId></project>' > "$mod/pom.xml"; \
    done

COPY mvnw .
RUN chmod +x mvnw \
    && ./mvnw -N install -DskipTests -q \
    && ./mvnw -pl common-lib install -DskipTests -q \
    && ./mvnw -pl {service-name} package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S twende && adduser -S twende -G twende
WORKDIR /app
COPY --from=build /workspace/{service-name}/target/{service-name}-*.jar app.jar
USER twende
EXPOSE {port}
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:{port}/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Gateway Dockerfile (no common-lib dependency)

The `api-gateway` does NOT depend on `common-lib` (WebFlux vs WebMvc conflict), so its
Dockerfile is simpler. It still needs `curl`, stub POMs, and `-N install`.

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache curl
WORKDIR /workspace

COPY pom.xml .
COPY api-gateway api-gateway

# Create minimal pom stubs for sibling modules referenced in parent pom
RUN for mod in common-lib auth-service country-config-service user-service driver-service \
    location-service pricing-service matching-service ride-service payment-service \
    subscription-service notification-service loyalty-service rating-service \
    analytics-service compliance-service; do \
    mkdir -p "$mod"; \
    echo '<project><modelVersion>4.0.0</modelVersion><parent><groupId>tz.co.twende</groupId><artifactId>twende-parent</artifactId><version>1.0.0-SNAPSHOT</version></parent><artifactId>'"$mod"'</artifactId></project>' > "$mod/pom.xml"; \
    done

COPY mvnw .
RUN chmod +x mvnw \
    && ./mvnw -N install -DskipTests -q \
    && ./mvnw -pl api-gateway package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S twende && adduser -S twende -G twende
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar
USER twende
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Rules:**
- **`apk add curl`** in the build stage (Maven wrapper needs it)
- **Stub POMs** for all sibling modules (parent POM references them)
- **`./mvnw -N install`** before building child modules (installs parent POM)
- **Never `COPY .mvn .mvn`** — the `.mvn` directory does not exist in this project
- Runs as non-root `twende` user
- Uses Alpine JRE for minimal image size
- Health check via `wget` to actuator endpoint (not `curl` — `curl` is not in the JRE image)
- All config via environment variables (no secrets in image)
- Build context is the monorepo root (not the service directory)
- Each service's Implementation Steps must include a Dockerfile step

### OpenAPI / Swagger (All REST Services)

Every REST service (not api-gateway, not common-lib) must configure SpringDoc OpenAPI:

```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("{Service Name} API")
                        .version("1.0")
                        .description("{service purpose}"));
    }
}
```

Swagger UI available at `http://localhost:{port}/swagger-ui.html`.

### Resilience for External API Calls

Services calling external APIs (Google Maps, Selcom, Africa's Talking, FCM, SUMATRA) must:
- Wrap calls in try-catch with meaningful error logging
- Use timeouts (5s default via RestClient)
- For payment-service: use Resilience4j `@CircuitBreaker` on Selcom calls
- For notification-service: log and continue on SMS/push failures (don't block ride flow)
- For compliance-service: store failed submissions, retry via scheduler

---

## 19. Multi-Country Strategy

The `country-config-service` is the single source of truth for all country-specific behaviour.
Every service that needs country-aware logic fetches its config from country-config-service
(via REST with Redis caching). When a new country is onboarded:

1. Insert a new `country_config` record (currency, locales, vehicle types, payment aggregator,
   regulatory authority, feature flags, operating cities).
2. Flip `status` to `active` in the admin API.
3. No code changes. No deployments.
