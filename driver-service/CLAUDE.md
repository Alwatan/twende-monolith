# CLAUDE.md -- Driver Service

> Twende Platform driver-service. Read this file fully before writing any code.

---

## 1. Overview

Manages driver profiles, document verification, vehicle registration, and driver online/offline
status. Coordinates with the subscription-service to enforce the "no subscription = no online"
rule before allowing a driver to come online.

**Port:** 8084
**Database:** `twende_drivers`
**Base package:** `com.twende.driver`
**Shared library:** `com.twende.common` (common-lib dependency)

This is a standalone Spring Boot microservice in the Twende monorepo. It does NOT use Eureka,
Feign, or Spring Cloud. Inter-service communication uses Spring `RestClient` with direct URLs
resolved from configuration. Authentication context arrives via gateway-injected headers.

---

## 2. Package Structure

```
com.twende.driver
├── DriverServiceApplication.java
├── config/
│   ├── RedisConfig.java
│   ├── KafkaConfig.java
│   ├── JpaConfig.java                  # @EnableJpaAuditing
│   ├── AsyncConfig.java                # @EnableAsync thread pool
│   ├── MinioConfig.java                # MinIO client bean
│   └── OpenApiConfig.java
├── entity/
│   ├── DriverProfile.java              # extends BaseEntity
│   ├── DriverVehicle.java              # extends BaseEntity
│   ├── DriverDocument.java             # extends BaseEntity
│   └── DriverStatusLog.java            # extends BaseEntity
├── repository/
│   ├── DriverProfileRepository.java
│   ├── DriverVehicleRepository.java
│   ├── DriverDocumentRepository.java
│   └── DriverStatusLogRepository.java
├── service/
│   ├── DriverService.java              # Profile CRUD, go-online/offline logic
│   ├── DocumentService.java            # Upload, verify, reject documents
│   ├── VehicleService.java             # Vehicle registration CRUD
│   └── DriverApprovalService.java      # Admin approval/rejection/suspension
├── client/
│   ├── SubscriptionClient.java         # RestClient to subscription-service /internal API
│   └── LocationClient.java             # RestClient to location-service (GEO index)
├── kafka/
│   ├── DriverEventPublisher.java       # Publishes to twende.drivers.*
│   └── DriverEventConsumer.java        # Consumes twende.users.*, twende.rides.*, twende.subscriptions.*
├── controller/
│   ├── DriverController.java           # /api/v1/drivers/me/** (self-service)
│   ├── DriverAdminController.java      # /api/v1/drivers/** (admin)
│   └── DriverInternalController.java   # /internal/drivers/** (service-to-service)
├── dto/
│   ├── request/
│   │   ├── UpdateDriverRequest.java
│   │   ├── RegisterVehicleRequest.java
│   │   ├── UpdateStatusRequest.java
│   │   └── ApprovalRequest.java
│   ├── response/
│   │   ├── DriverProfileDto.java
│   │   ├── DriverVehicleDto.java
│   │   ├── DriverDocumentDto.java
│   │   ├── DriverSummaryDto.java
│   │   └── ActiveVehicleDto.java
│   └── event/
│       ├── DriverStatusUpdatedEvent.java
│       ├── DriverApprovedEvent.java
│       ├── UserRegisteredEvent.java
│       ├── RideCompletedEvent.java
│       ├── SubscriptionActivatedEvent.java
│       └── SubscriptionExpiredEvent.java
└── mapper/
    └── DriverMapper.java               # MapStruct entity <-> DTO
```

---

## 3. Database Schema

Database: `twende_drivers` (isolated per-service database).
Schema managed by Flyway. Migrations in `src/main/resources/db/migration/`.

```sql
-- V1__create_driver_schema.sql

CREATE TYPE driver_status AS ENUM (
    'PENDING_APPROVAL', 'APPROVED', 'OFFLINE', 'ONLINE_AVAILABLE',
    'ONLINE_ON_TRIP', 'SUSPENDED', 'REJECTED'
);

CREATE TABLE drivers (
    id                UUID         PRIMARY KEY,  -- same UUID as in auth-service
    country_code      CHAR(2)      NOT NULL,
    full_name         VARCHAR(150) NOT NULL,
    email             VARCHAR(255),
    profile_photo_url VARCHAR(500),
    status            driver_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    rejection_reason  TEXT,
    approved_at       TIMESTAMPTZ,
    approved_by       UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE driver_vehicles (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id       UUID         NOT NULL REFERENCES drivers(id),
    vehicle_type    VARCHAR(30)  NOT NULL,     -- VehicleType enum: BAJAJ, BODA_BODA, ECONOMY_CAR
    make            VARCHAR(50),               -- "Bajaj", "Honda"
    model           VARCHAR(50),
    year            INT,
    plate_number    VARCHAR(20)  NOT NULL,
    color           VARCHAR(30),
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(driver_id, plate_number)
);

CREATE TABLE driver_documents (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id        UUID         NOT NULL REFERENCES drivers(id),
    document_type    VARCHAR(50)  NOT NULL,     -- DocumentType enum
    file_url         VARCHAR(500) NOT NULL,     -- MinIO (S3-compatible) URL
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, VERIFIED, REJECTED
    rejection_reason TEXT,
    verified_by      UUID,
    verified_at      TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(driver_id, document_type)
);

CREATE TABLE driver_status_log (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id    UUID          NOT NULL REFERENCES drivers(id),
    from_status  driver_status,
    to_status    driver_status NOT NULL,
    reason       TEXT,
    changed_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_driver_status ON drivers(status);
CREATE INDEX idx_driver_country ON drivers(country_code, status);
CREATE INDEX idx_documents_driver ON driver_documents(driver_id);
```

---

## 4. DriverStatus Enum

```
PENDING_APPROVAL  -- new driver, documents not yet verified
APPROVED          -- documents verified, admin approved, but currently offline
OFFLINE           -- approved driver who is not accepting rides
ONLINE_AVAILABLE  -- actively accepting rides (requires active subscription + vehicle)
ONLINE_ON_TRIP    -- currently on a ride
SUSPENDED         -- temporarily blocked by admin
REJECTED          -- application denied by admin
```

**State transitions:**
```
PENDING_APPROVAL --> APPROVED       (admin approves)
PENDING_APPROVAL --> REJECTED       (admin rejects)
APPROVED         --> ONLINE_AVAILABLE (driver goes online -- validated)
APPROVED         --> OFFLINE        (explicit offline)
OFFLINE          --> ONLINE_AVAILABLE (driver goes online -- validated)
ONLINE_AVAILABLE --> ONLINE_ON_TRIP (ride accepted)
ONLINE_AVAILABLE --> OFFLINE        (driver goes offline)
ONLINE_ON_TRIP   --> ONLINE_AVAILABLE (ride completed)
ONLINE_ON_TRIP   --> OFFLINE        (ride completed + goes offline)
ANY              --> SUSPENDED      (admin suspends)
SUSPENDED        --> APPROVED       (admin reinstates)
```

All status changes are logged in `driver_status_log`.

---

## 5. API Endpoints

### Driver Self-Service (requires authenticated driver)

Identity is read from gateway headers (`X-User-Id`, `X-User-Role`, `X-Country-Code`),
NOT from the request body.

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/v1/drivers/me` | Get own driver profile |
| `PUT`  | `/api/v1/drivers/me` | Update profile (name, email, photo) |
| `POST` | `/api/v1/drivers/me/documents` | Upload a document (multipart, max 10 MB) |
| `GET`  | `/api/v1/drivers/me/documents` | List submitted documents |
| `POST` | `/api/v1/drivers/me/vehicles` | Register a vehicle |
| `GET`  | `/api/v1/drivers/me/vehicles` | List registered vehicles |
| `PUT`  | `/api/v1/drivers/me/status` | Go online / go offline |
| `GET`  | `/api/v1/drivers/me/summary` | Earnings and trip summary |

### Admin (requires `X-User-Role: ADMIN`)

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/v1/drivers` | Paginated driver list with status/country filters |
| `GET`  | `/api/v1/drivers/{id}` | Get driver detail (profile + docs + vehicles) |
| `PUT`  | `/api/v1/drivers/{id}/approval` | Approve or reject driver |
| `PUT`  | `/api/v1/drivers/{id}/documents/{docId}/verify` | Verify or reject a document |
| `POST` | `/api/v1/drivers/{id}/suspend` | Suspend driver |

### Internal (service-to-service, no gateway, no auth headers required)

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/internal/drivers/{id}` | Get driver by ID (used by matching-service) |
| `GET`  | `/internal/drivers/{id}/active-vehicle` | Get driver's active vehicle type and details |

Internal endpoints are called directly by other services (matching-service, ride-service)
using RestClient. They bypass the API gateway and are not exposed publicly.

### Response Wrapper

Every endpoint returns `ApiResponse<T>` from common-lib:
```java
@GetMapping("/me")
public ResponseEntity<ApiResponse<DriverProfileDto>> getProfile(
        @RequestHeader("X-User-Id") UUID userId) {
    return ResponseEntity.ok(ApiResponse.ok(driverService.getProfile(userId)));
}
```

---

## 6. Kafka Topics

### Published

| Topic | Event Payload | Trigger |
|-------|---------------|---------|
| `twende.drivers.status-updated` | `DriverStatusUpdatedEvent` | Driver goes online, offline, or on-trip |
| `twende.drivers.approved` | `DriverApprovedEvent` | Admin approves a driver |

### Consumed

| Topic | Event Payload | Action |
|-------|---------------|--------|
| `twende.users.registered` | `UserRegisteredEvent` | Create driver profile if `role == DRIVER` |
| `twende.rides.completed` | `RideCompletedEvent` | Update driver trip count and last trip timestamp |
| `twende.subscriptions.activated` | `SubscriptionActivatedEvent` | Cache subscription status (allows go-online) |
| `twende.subscriptions.expired` | `SubscriptionExpiredEvent` | Force driver offline if currently `ONLINE_AVAILABLE` |

**Consumer group:** `driver-service-group`

**Event processing rules:**
- All consumers are idempotent -- duplicate events must not create duplicate records
- On `UserRegisteredEvent`: only create profile if `role == DRIVER` and profile does not already exist
- On `SubscriptionExpiredEvent`: transition driver to `OFFLINE`, log status change, publish `twende.drivers.status-updated`

---

## 7. Service Logic

### Go-Online Flow

When a driver calls `PUT /api/v1/drivers/me/status` with `{ "status": "ONLINE_AVAILABLE" }`:

```java
public void goOnline(UUID driverId) {
    DriverProfile driver = findById(driverId);

    // 1. Must be APPROVED (or OFFLINE -- already approved)
    if (driver.getStatus() != DriverStatus.APPROVED
            && driver.getStatus() != DriverStatus.OFFLINE) {
        throw new BadRequestException("Driver must be approved to go online");
    }

    // 2. Check active subscription via RestClient to subscription-service
    boolean hasSubscription = subscriptionClient.hasActiveSubscription(driverId);
    if (!hasSubscription) {
        throw new PaymentRequiredException("Purchase a bundle to go online");
    }

    // 3. Must have at least one active vehicle
    if (!vehicleRepository.existsByDriverIdAndIsActiveTrue(driverId)) {
        throw new BadRequestException("Register a vehicle first");
    }

    // 4. Transition status
    DriverStatus oldStatus = driver.getStatus();
    driver.setStatus(DriverStatus.ONLINE_AVAILABLE);
    driverRepository.save(driver);

    // 5. Log status change
    logStatusChange(driverId, oldStatus, DriverStatus.ONLINE_AVAILABLE, null);

    // 6. Publish Kafka event (location-service begins tracking)
    driverEventPublisher.publishStatusUpdated(driver);
}
```

### Subscription Client (RestClient, NOT Feign)

```java
@Component
public class SubscriptionClient {
    private final RestClient restClient;

    public SubscriptionClient(
            @Value("${twende.services.subscription-service.url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public boolean hasActiveSubscription(UUID driverId) {
        try {
            restClient.get()
                .uri("/internal/subscriptions/{driverId}/active", driverId)
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }
}
```

### Document Verification Flow

```
1. Driver uploads document via POST /api/v1/drivers/me/documents
   - File uploaded to MinIO (S3-compatible), max 10 MB
   - MinIO path: twende-driver-documents/{driverId}/{documentType}/{filename}
   - DriverDocument created with status=PENDING, fileUrl=MinIO URL
2. Admin reviews via PUT /api/v1/drivers/{id}/documents/{docId}/verify
   - Sets status to VERIFIED or REJECTED (with rejection_reason)
3. When all required documents for the country are VERIFIED:
   - Driver becomes eligible for admin approval
4. Admin approves via PUT /api/v1/drivers/{id}/approval
   - Status transitions from PENDING_APPROVAL to APPROVED
   - Publishes DriverApprovedEvent to Kafka
5. notification-service picks up the event and notifies the driver
```

Required document types per country are resolved from country-config-service.

### Profile Creation via Kafka

```java
@KafkaListener(topics = "twende.users.registered", groupId = "driver-service-group")
public void onUserRegistered(UserRegisteredEvent event) {
    if (event.getRole() != UserRole.DRIVER) return;
    if (driverRepository.existsById(event.getUserId())) return;  // idempotent

    DriverProfile driver = new DriverProfile();
    driver.setId(event.getUserId());
    driver.setCountryCode(event.getCountryCode());
    driver.setFullName(event.getFullName());
    driver.setStatus(DriverStatus.PENDING_APPROVAL);
    driverRepository.save(driver);
}
```

### Forced Offline on Subscription Expiry

```java
@KafkaListener(topics = "twende.subscriptions.expired", groupId = "driver-service-group")
public void onSubscriptionExpired(SubscriptionExpiredEvent event) {
    DriverProfile driver = driverRepository.findById(event.getDriverId()).orElse(null);
    if (driver == null) return;
    if (driver.getStatus() == DriverStatus.ONLINE_AVAILABLE) {
        driver.setStatus(DriverStatus.OFFLINE);
        driverRepository.save(driver);
        logStatusChange(driver.getId(), DriverStatus.ONLINE_AVAILABLE, DriverStatus.OFFLINE,
                "Subscription expired");
        driverEventPublisher.publishStatusUpdated(driver);
    }
}
```

---

## 8. Authentication and Authorization

This service does NOT validate JWTs directly. The API gateway handles JWT validation and
forwards identity via headers:

| Header | Description |
|--------|-------------|
| `X-User-Id` | UUID of the authenticated user |
| `X-User-Role` | `RIDER`, `DRIVER`, or `ADMIN` |
| `X-Country-Code` | Two-letter country code (e.g., `TZ`) |

**Endpoint security rules:**
- `/api/v1/drivers/me/**` -- requires `X-User-Role: DRIVER`
- `/api/v1/drivers/**` (admin) -- requires `X-User-Role: ADMIN`
- `/internal/**` -- no auth headers; must only be reachable from internal network
- `/actuator/health` -- public

---

## 9. Application Configuration

```yaml
server:
  port: 8084

spring:
  application:
    name: driver-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_drivers
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
    hikari:
      maximum-pool-size: 15
      minimum-idle: 5
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
      group-id: driver-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.twende.*
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

twende:
  services:
    subscription-service:
      url: ${SUBSCRIPTION_SERVICE_URL:http://localhost:8086}
    location-service:
      url: ${LOCATION_SERVICE_URL:http://localhost:8085}
    country-config-service:
      url: ${COUNTRY_CONFIG_SERVICE_URL:http://localhost:8082}
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:twende}
    secret-key: ${MINIO_SECRET_KEY:twende123}
    bucket: twende-driver-documents
  documents:
    max-file-size: 10485760  # 10 MB

logging:
  level:
    com.twende: DEBUG
    org.springframework.kafka: WARN
```

---

## 10. Conventions

**These apply to every file in this service. Never deviate.**

- All entities extend `BaseEntity` from common-lib (ULID-based UUID PK, createdAt, updatedAt, countryCode)
- Money fields use `BigDecimal` -- never `double` or `float`
- Timestamps use `Instant` -- never `LocalDateTime` or `Date`
- All controller methods return `ApiResponse<T>` from common-lib
- Validate all incoming requests with `@Valid @RequestBody`
- No cross-service repository access -- use RestClient to call other services' internal APIs
- No Feign, no Eureka, no Spring Cloud -- use Spring `RestClient` for inter-service calls
- Profile photo uploads use a separate multipart endpoint with image validation
- Document file size limit: 10 MB (enforced at both gateway and service level)
- MapStruct for entity-to-DTO mapping
- Lombok for boilerplate reduction (`@Getter @Setter @NoArgsConstructor` on entities)

### Test naming convention
```java
@Test
void givenApprovedDriverWithSubscription_whenGoOnline_thenStatusIsOnlineAvailable() { ... }

@Test
void givenNoActiveSubscription_whenGoOnline_thenThrowsPaymentRequired() { ... }
```

### Coverage
- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw verify`
- Report: `target/site/jacoco/index.html`
- Excluded from coverage: entities, DTOs, enums, config classes, `DriverServiceApplication`

---

## Implementation Steps

Work through these in order. Do not skip ahead.

- [ ] **1. application.yml** — Configure port 8084, datasource `twende_drivers`, Redis connection, Kafka consumer (`driver-service-group`, earliest offset, `JsonDeserializer` with trusted packages) and producer (`JsonSerializer`), MinIO endpoint/credentials/bucket, subscription-service URL, location-service URL, country-config-service URL, document max file size (10 MB)
- [ ] **2. Entities** — Create `DriverProfile` (extends `BaseEntity`, maps to `drivers` table, ID set explicitly from Kafka event), `DriverVehicle` (extends `BaseEntity`, maps to `driver_vehicles`), `DriverDocument` (extends `BaseEntity`, maps to `driver_documents`), `DriverStatusLog` (extends `BaseEntity`, maps to `driver_status_log`)
- [ ] **3. Repositories** — Create `DriverProfileRepository`, `DriverVehicleRepository` (with `existsByDriverIdAndIsActiveTrue`), `DriverDocumentRepository` (with `findByDriverId`, unique constraint on `driverId + documentType`), `DriverStatusLogRepository`
- [ ] **4. DriverService** — Implement profile CRUD, go-online validation (must be APPROVED or OFFLINE + active subscription via `SubscriptionClient` + active vehicle), go-offline, status transitions with logging to `driver_status_log`. Publish `DriverStatusUpdatedEvent` to Kafka on every status change
- [ ] **5. DocumentService** — Upload document file to MinIO at path `twende-driver-documents/{driverId}/{documentType}/{filename}`, create `DriverDocument` with status `PENDING` and `fileUrl`. Admin verify/reject endpoint updates status and sets `verifiedBy`, `verifiedAt`, `rejectionReason`
- [ ] **6. VehicleService** — Register vehicle (validate vehicle type against country-config-service required types), list vehicles, set active vehicle. Enforce unique constraint on `driverId + plateNumber`
- [ ] **7. DriverApprovalService** — Admin approve (transition `PENDING_APPROVAL` to `APPROVED`, set `approvedAt` and `approvedBy`, publish `DriverApprovedEvent` to Kafka). Admin reject (set `REJECTED` with `rejectionReason`). Admin suspend (any status to `SUSPENDED`). Admin reinstate (`SUSPENDED` to `APPROVED`)
- [ ] **8. Kafka consumer** — `DriverEventConsumer` listening on: `twende.users.registered` (create profile if `role == DRIVER`, idempotent), `twende.rides.completed` (update driver trip count and last trip timestamp), `twende.subscriptions.activated` (cache subscription status), `twende.subscriptions.expired` (force driver offline if `ONLINE_AVAILABLE`, log status change, publish status-updated event)
- [ ] **9. Kafka producer** — `DriverEventPublisher` publishing to `twende.drivers.status-updated` (on status changes) and `twende.drivers.approved` (on admin approval)
- [ ] **10. SubscriptionClient** — Spring `RestClient` calling subscription-service at `/internal/subscriptions/{driverId}/active`. Returns `true` on 200, `false` on 404. No Feign
- [ ] **11. LocationClient** — Spring `RestClient` calling location-service for GEO index operations (add/remove driver from spatial index on go-online/go-offline)
- [ ] **12. MinioConfig** — Configure MinIO client bean with endpoint, access key, secret key from application properties. Ensure bucket `twende-driver-documents` exists on startup
- [ ] **13. DriverController** — Self-service endpoints under `/api/v1/drivers/me/**` (profile, documents, vehicles, status, summary). Read identity from `X-User-Id` header. All responses wrapped in `ApiResponse<T>`. Validate requests with `@Valid @RequestBody`
- [ ] **14. DriverAdminController** — Admin endpoints under `/api/v1/drivers/**` (paginated list with filters, driver detail, approval, document verification, suspend). Require `X-User-Role: ADMIN`
- [ ] **15. DriverInternalController** — Internal endpoints under `/internal/drivers/**` (get driver by ID, get active vehicle). No auth headers required. Called by matching-service and ride-service via RestClient
- [ ] **16. DTOs + MapStruct mapper** — Request DTOs (`UpdateDriverRequest`, `RegisterVehicleRequest`, `UpdateStatusRequest`, `ApprovalRequest`) with validation annotations. Response DTOs (`DriverProfileDto`, `DriverVehicleDto`, `DriverDocumentDto`, `DriverSummaryDto`, `ActiveVehicleDto`). Kafka event DTOs. Create `DriverMapper` with MapStruct
- [ ] **17. Flyway migration** — `V1__create_driver_schema.sql` with `drivers` table (PK not auto-generated), `driver_vehicles`, `driver_documents`, `driver_status_log` tables, indexes, and `driver_status` enum type
- [ ] **18. Unit tests + integration tests** — Unit tests for `DriverService` (go-online validation, status transitions), `DocumentService`, `VehicleService`, `DriverApprovalService`, `DriverEventConsumer` (idempotency, role filtering, forced offline). Integration tests with Testcontainers (PostgreSQL + Kafka) covering all endpoints, status transitions, Kafka consumption and publishing
- [ ] **19. Dockerfile** — Multi-stage build (eclipse-temurin:21-jdk-alpine for build, 21-jre-alpine for run). Non-root `twende` user. Health check on `/actuator/health`. Expose port 8084.
- [ ] **20. OpenAPI config** — `OpenApiConfig.java` with SpringDoc `OpenAPI` bean. Title: "Driver Service API". Swagger UI at `/swagger-ui.html`.
- [ ] **21. Verify build** — Run `./mvnw -pl driver-service clean verify` and confirm all tests pass with minimum 80% line coverage
