# CLAUDE.md — Twende Platform

> This file is the single source of truth for Claude Code when building Twende.
> Read it fully before writing any code. Each service also has its own `CLAUDE.md`
> with service-specific implementation details — read both this file and the
> relevant service file before working on any module.

---

## 1. What is Twende?

Twende ("Let's Go" in Swahili) is a ride-hailing platform for African markets, launching
first in **Tanzania**. The core competitive advantage is the **driver subscription model**:
drivers pay a daily / weekly / monthly bundle and keep **100% of their earnings** — no
per-trip commission. This is fundamentally different from Uber and Bolt.

**Key differentiating features (build these, do not simplify them away):**
- Driver subscription bundles (daily TSh 2,000 / weekly TSh 10,000 / monthly TSh 35,000)
- Fare boost: rider can increase their offer to attract faster acceptance
- Rejection counter: rider sees how many drivers passed on their trip
- Trip start OTP: rider shares a 4-digit code with the driver to begin the trip
- Broadcast-and-accept matching: offer is sent to ALL nearby eligible drivers simultaneously
- Multi-country config: Tanzania now, Kenya and Uganda without code changes later

**Vehicle types (Tanzania):** Bajaj (tuk-tuk), Boda Boda (motorcycle), Economy Car

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
  │  broadcasts offer to ~10 drivers via push notification
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
The custom `UlidGenerator` produces monotonically increasing IDs for better B-tree index
performance.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
public abstract class BaseEntity {
    @Id
    @GeneratedValue(generator = "ulid")
    @GenericGenerator(name = "ulid", type = UlidGenerator.class)
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

1. **Driver keeps 100%** — ride fare goes entirely to the driver. Twende earns from
   subscription bundles only. Never deduct a percentage from ride payment to Twende.

2. **No subscription = no online** — a driver with an expired subscription cannot set
   status to `ONLINE_AVAILABLE`. Hard block in driver-service.

3. **Fare can only go up** — a rider can boost the fare during `REQUESTED` status but
   cannot reduce it. Once boosted, base fare + boost is the floor.

4. **OTP is single-use** — null out `tripStartOtpHash` immediately after successful
   verification. Never re-use the same OTP.

5. **Money arithmetic uses BigDecimal only** — never `double`, never `float`.

6. **Trip start requires physical presence** — OTP must be entered by the driver after
   arriving. Do not allow drivers to mark `IN_PROGRESS` without a valid OTP. No exceptions.

7. **Country config is read-only at runtime** — only admin endpoints can modify country
   config. Services read it (with caching) but never write it.

8. **Driver rejection is permanent for that ride** — once a driver rejects a ride,
   they must not receive another offer for the same ride, even if the fare is boosted.
   Check `driver_rejected:{rideId}` Redis set before re-broadcasting.

9. **Phone numbers in E.164 format** — always normalise via `PhoneUtil.normalise()` before
   storing. `+255712345678`, not `0712345678` or `255712345678`.

10. **Wallet updates are always transactional** — balance update and wallet_entry insert
    in one `@Transactional` method. Never update the balance without an entry.

11. **Riders pay cash only** — all rider payments are physical cash at end of trip.
    No digital payment processing on the rider side. Selcom is only for driver
    subscriptions and wallet payouts.

12. **Free rides are Twende's cost** — when a loyalty free ride completes, Twende
    credits the driver's wallet with the full calculated fare. The driver is never
    penalised for accepting a free ride.

13. **Free ride offers are vehicle-type-specific** — an offer earned on Bajaj rides
    can only be redeemed on a Bajaj ride. Offers do not transfer across vehicle types.

14. **Free ride offers are distance-capped** — a free ride offer specifies a maximum
    distance (km). If the rider's requested trip exceeds this distance, the offer
    cannot be applied to that ride.

15. **Free rides don't count toward the next offer** — a completed free ride does not
    increment the rider's progress toward earning the next free ride offer.

16. **Rides cannot start in RESTRICTED zones** — if the pickup point falls inside a zone
    with type `RESTRICTED`, reject the ride request. No exceptions.

17. **Zone checks use PostGIS, not application code** — all point-in-polygon checks must
    use `ST_Covers` via native queries in `ZoneRepository`. Never iterate polygons in Java.

18. **Provider switching is per-city, not global** — each `OperatingCity` has its own
    `geocoding_provider`, `routing_provider`, and `autocomplete_provider` columns.
    Changing a provider for one city must not affect other cities.

19. **Google Maps API key is never exposed to clients** — all mapping API calls are
    server-side via the location-service. Frontend gets results from our endpoints only.

20. **SMS and push provider switching is per-country** — `CountryConfig.smsProvider` and
    `CountryConfig.pushProvider` determine which implementation handles notifications.
    Changing a provider for one country must not affect other countries.

21. **No services call SMS/push providers directly** — all notification sending goes through
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

### Step 2: Build, Tests & Coverage
```bash
./mvnw clean verify
```
Requirements:
- All tests MUST pass (zero failures, zero errors)
- No skipped or ignored tests unless justified
- JaCoCo coverage >= 80% line coverage on all non-excluded classes

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
3. **Run ALL pre-push checks from Section 12** (format, verify, trivy)
4. **Minimum 80% line coverage** on all new code. If below 80%, write more tests.
5. Fix any vulnerabilities or secrets found by Trivy before committing.
6. Once all checks pass, commit and push.

**Detailed implementation sub-steps are in each service's `CLAUDE.md` file.** The phases
below define the order and what services to build. For the step-by-step breakdown of HOW
to build each service, refer to the **"Implementation Steps"** section in that service's
`{service}/CLAUDE.md`.

### Phase 1 — Foundation
- [ ] `common-lib` — see `common-lib/CLAUDE.md` → Implementation Steps
- [ ] `auth-service` — see `auth-service/CLAUDE.md` → Implementation Steps
- [ ] `api-gateway` — see `api-gateway/CLAUDE.md` → Implementation Steps

### Phase 2 — Core Data
- [ ] `country-config-service` — see `country-config-service/CLAUDE.md` → Implementation Steps
- [ ] `user-service` — see `user-service/CLAUDE.md` → Implementation Steps
- [ ] `driver-service` — see `driver-service/CLAUDE.md` → Implementation Steps

### Phase 3 — Ride Flow
- [ ] `location-service` — see `location-service/CLAUDE.md` → Implementation Steps
- [ ] `pricing-service` — see `pricing-service/CLAUDE.md` → Implementation Steps
- [ ] `matching-service` — see `matching-service/CLAUDE.md` → Implementation Steps
- [ ] `ride-service` — see `ride-service/CLAUDE.md` → Implementation Steps

### Phase 4 — Commerce
- [ ] `payment-service` — see `payment-service/CLAUDE.md` → Implementation Steps
- [ ] `subscription-service` — see `subscription-service/CLAUDE.md` → Implementation Steps
- [ ] `loyalty-service` — see `loyalty-service/CLAUDE.md` → Implementation Steps

### Phase 5 — Supporting Features
- [ ] `notification-service` — see `notification-service/CLAUDE.md` → Implementation Steps
- [ ] `rating-service` — see `rating-service/CLAUDE.md` → Implementation Steps

### Phase 6 — Admin & Observability
- [ ] `analytics-service` — see `analytics-service/CLAUDE.md` → Implementation Steps
- [ ] `compliance-service` — see `compliance-service/CLAUDE.md` → Implementation Steps
- [ ] Admin endpoints across all services (`X-User-Role == ADMIN` check)
- [ ] Prometheus metrics exposed at `/actuator/prometheus`
- [ ] Zipkin tracing configured

---

## 14. Testing Strategy

### Unit tests — for pure logic
- All `util/` classes
- `PricingService.calculateFare(...)` — test all edge cases
- OTP generation, expiry, attempts
- State machine transitions in ride-service

### Integration tests — use Testcontainers
```java
@SpringBootTest
@Testcontainers
class RideFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
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
        spring.json.trusted.packages: com.twende.common.events.*
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

## 18. Multi-Country Strategy

The `country-config-service` is the single source of truth for all country-specific behaviour.
Every service that needs country-aware logic fetches its config from country-config-service
(via REST with Redis caching). When a new country is onboarded:

1. Insert a new `country_config` record (currency, locales, vehicle types, payment aggregator,
   regulatory authority, feature flags, operating cities).
2. Flip `status` to `active` in the admin API.
3. No code changes. No deployments.
