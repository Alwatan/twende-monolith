# CLAUDE.md -- Twende User Service

> Service-level instructions for Claude Code. Read fully before writing any code.
> The root `/CLAUDE.md` contains platform-wide conventions -- follow those too.

---

## 1. Overview

The user-service manages **rider profiles**, preferences, and saved places. The auth-service
owns identity (phone number, credentials, JWT issuance). This service owns everything about
the rider's profile and app experience.

- **Port:** 8083
- **Database:** `twende_users` (PostgreSQL 16, Flyway-managed)
- **Base package:** `tz.co.twende.user`
- **Scans:** `tz.co.twende.user` + `tz.co.twende.common` (from common-lib)

The user ID is always the **same UUID** as the corresponding `AuthUser` in auth-service.
It is never generated here -- it comes from the `UserRegisteredEvent` Kafka message.

---

## 2. Package Structure

```
tz.co.twende.user
├── UserServiceApplication.java
├── config/
│   ├── KafkaConfig.java              # Consumer + producer factory beans
│   ├── RedisConfig.java              # RedisTemplate, cache manager
│   ├── RestClientConfig.java         # RestClient bean for ride-service calls
│   └── OpenApiConfig.java            # SpringDoc with X-User-Id header
├── entity/
│   ├── UserProfile.java              # Extends BaseEntity
│   └── SavedPlace.java               # Extends BaseEntity
├── repository/
│   ├── UserProfileRepository.java
│   └── SavedPlaceRepository.java
├── service/
│   ├── UserService.java              # Profile CRUD, ride history proxy
│   └── SavedPlaceService.java        # Saved places CRUD
├── controller/
│   ├── UserController.java           # /api/v1/users/**
│   └── SavedPlaceController.java     # /api/v1/users/me/saved-places/**
├── dto/
│   ├── UserProfileDto.java
│   ├── UpdateProfileRequest.java
│   ├── SavedPlaceDto.java
│   ├── CreateSavedPlaceRequest.java
│   └── RideHistoryResponse.java      # Proxied from ride-service
├── mapper/
│   └── UserMapper.java               # MapStruct: entity <-> DTO
├── kafka/
│   ├── UserRegisteredConsumer.java    # Listens on twende.users.registered
│   ├── UserProfileUpdatedProducer.java # Publishes to twende.users.profile-updated
│   └── event/
│       ├── UserRegisteredEvent.java   # Inbound Kafka event DTO
│       └── UserProfileUpdatedEvent.java # Outbound Kafka event DTO
└── client/
    └── RideServiceClient.java         # RestClient calls to ride-service
```

---

## 3. Database Schema

**Database name:** `twende_users`

### V1__create_users_schema.sql

```sql
CREATE TABLE users (
    id                       UUID         PRIMARY KEY,  -- same UUID as auth-service AuthUser
    country_code             CHAR(2)      NOT NULL,
    full_name                VARCHAR(150) NOT NULL,
    email                    VARCHAR(255),
    profile_photo_url        VARCHAR(500),
    preferred_locale         VARCHAR(10),
    preferred_payment_method VARCHAR(30),
    is_active                BOOLEAN      NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE saved_places (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label        VARCHAR(50)  NOT NULL,  -- "Home", "Work", or custom
    address      VARCHAR(300) NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_saved_places_user ON saved_places(user_id);
```

### Key schema rules

- The `users.id` column is NOT auto-generated. It is set from the `UserRegisteredEvent`
  payload -- it must match the auth-service `AuthUser.id` exactly.
- `saved_places.id` uses `gen_random_uuid()` as a DB default but application code should
  still set it via `UlidGenerator` from common-lib for consistency.
- Money columns (if any are added later) must use `NUMERIC(12,2)`, never `FLOAT` or `DOUBLE PRECISION`.
- Timestamps are always `TIMESTAMPTZ` (UTC).
- JPA ddl-auto is `validate` -- Flyway manages all schema changes.

---

## 4. API Endpoints

All endpoints read the calling user from the **`X-User-Id` header** injected by the
API gateway. Never trust user identity from the request body.

Supporting headers from gateway: `X-User-Role`, `X-Country-Code`.

### Rider-facing endpoints

| Method   | Path                                | Description                    | Auth     |
|----------|-------------------------------------|--------------------------------|----------|
| `GET`    | `/api/v1/users/me`                  | Get own profile                | RIDER    |
| `PUT`    | `/api/v1/users/me`                  | Update own profile             | RIDER    |
| `POST`   | `/api/v1/users/me/photo`            | Upload profile photo           | RIDER    |
| `GET`    | `/api/v1/users/me/saved-places`     | List saved places              | RIDER    |
| `POST`   | `/api/v1/users/me/saved-places`     | Add a saved place              | RIDER    |
| `DELETE` | `/api/v1/users/me/saved-places/{id}`| Remove a saved place           | RIDER    |
| `GET`    | `/api/v1/users/me/ride-history`     | Paginated ride history         | RIDER    |

### Admin / internal endpoints

| Method   | Path                                | Description                    | Auth     |
|----------|-------------------------------------|--------------------------------|----------|
| `GET`    | `/api/v1/users/{id}`                | Get user by ID                 | ADMIN    |

### Response format

Every endpoint returns `ApiResponse<T>` from common-lib:
```java
@GetMapping("/me")
public ResponseEntity<ApiResponse<UserProfileDto>> getProfile(
        @RequestHeader("X-User-Id") UUID userId) {
    return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
}
```

### Validation

All request DTOs use Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, etc.).
Controllers accept `@Valid @RequestBody`.

```java
public class UpdateProfileRequest {
    @Size(min = 2, max = 150)
    private String fullName;

    @Email
    private String email;

    @Size(max = 10)
    private String preferredLocale;
}

public class CreateSavedPlaceRequest {
    @NotBlank @Size(max = 50)
    private String label;

    @NotBlank @Size(max = 300)
    private String address;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;
}
```

---

## 5. Kafka Topics

### Consumed

| Topic                      | Event                  | Action                                |
|----------------------------|------------------------|---------------------------------------|
| `twende.users.registered`  | `UserRegisteredEvent`  | Create `users` row if role is `RIDER` |

**Consumer group:** `user-service-group`

**Event payload (inbound):**
```json
{
  "userId": "01917a5c-...",
  "phoneNumber": "+255712345678",
  "fullName": "Jane Doe",
  "role": "RIDER",
  "countryCode": "TZ",
  "timestamp": "2026-04-07T10:00:00Z"
}
```

**Consumer logic:**
```java
@KafkaListener(topics = "twende.users.registered", groupId = "user-service-group")
public void onUserRegistered(UserRegisteredEvent event) {
    if (!"RIDER".equals(event.getRole())) return;   // ignore DRIVER registrations
    if (userProfileRepository.existsById(event.getUserId())) return;  // idempotent

    UserProfile profile = new UserProfile();
    profile.setId(event.getUserId());          // same UUID as auth-service
    profile.setFullName(event.getFullName());
    profile.setCountryCode(event.getCountryCode());
    profile.setIsActive(true);
    userProfileRepository.save(profile);
}
```

**Idempotency:** The consumer checks `existsById` before inserting. Duplicate events
are safely ignored. If the Kafka event arrives late, the mobile app can call
`POST /api/v1/users/me/init` as a fallback (idempotent creation).

### Published

| Topic                           | Event                      | Trigger                    |
|---------------------------------|----------------------------|----------------------------|
| `twende.users.profile-updated`  | `UserProfileUpdatedEvent`  | On `PUT /api/v1/users/me`  |

**Event payload (outbound):**
```json
{
  "userId": "01917a5c-...",
  "fullName": "Jane Doe Updated",
  "email": "jane@example.com",
  "countryCode": "TZ",
  "timestamp": "2026-04-07T12:00:00Z"
}
```

---

## 6. Service Logic

### UserService

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserProfileRepository userProfileRepository;
    private final UserProfileUpdatedProducer profileUpdatedProducer;
    private final RideServiceClient rideServiceClient;

    public UserProfileDto getProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return userMapper.toDto(profile);
    }

    @Transactional
    public UserProfileDto updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (request.getFullName() != null) profile.setFullName(request.getFullName());
        if (request.getEmail() != null) profile.setEmail(request.getEmail());
        if (request.getPreferredLocale() != null) profile.setPreferredLocale(request.getPreferredLocale());

        UserProfile saved = userProfileRepository.save(profile);

        // Publish profile-updated event to Kafka
        profileUpdatedProducer.send(new UserProfileUpdatedEvent(
            saved.getId(), saved.getFullName(), saved.getEmail(),
            saved.getCountryCode(), Instant.now()
        ));

        return userMapper.toDto(saved);
    }

    public PagedResponse<RideHistoryResponse> getRideHistory(UUID userId, int page, int size) {
        // Delegate to ride-service via RestClient (internal API)
        return rideServiceClient.getRideHistory(userId, page, size);
    }
}
```

### Ride history -- inter-service call via RestClient

```java
@Component
@RequiredArgsConstructor
public class RideServiceClient {
    private final RestClient restClient;

    public PagedResponse<RideHistoryResponse> getRideHistory(UUID userId, int page, int size) {
        return restClient.get()
            .uri("http://${RIDE_SERVICE_URL}/api/v1/rides/history?userId={userId}&page={page}&size={size}",
                userId, page, size)
            .header("X-User-Id", userId.toString())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }
}
```

**No Eureka, no Feign.** Inter-service calls use Spring `RestClient` with direct URLs
configured via environment variables.

### Profile photo upload

Profile photos are stored in **MinIO / S3**. The service stores only the URL in
`users.profile_photo_url`. Use multipart upload with a maximum file size (e.g., 5 MB).

---

## 7. Inter-Service Communication

| Direction          | Service        | Method     | Purpose                           |
|--------------------|----------------|------------|-----------------------------------|
| Inbound (Kafka)    | auth-service   | Consumer   | `UserRegisteredEvent` -> create profile |
| Outbound (Kafka)   | any subscriber | Producer   | `UserProfileUpdatedEvent`         |
| Outbound (REST)    | ride-service   | RestClient | Fetch rider's ride history        |

**RestClient configuration:**
```java
@Configuration
public class RestClientConfig {
    @Bean
    public RestClient rideServiceRestClient(
            @Value("${twende.services.ride-service.url}") String rideServiceUrl) {
        return RestClient.builder()
            .baseUrl(rideServiceUrl)
            .defaultHeader("Accept", "application/json")
            .build();
    }
}
```

---

## 8. Application Configuration

### application.yml

```yaml
server:
  port: 8083

spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_users
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
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: user-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "tz.co.twende.*"
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
    ride-service:
      url: ${RIDE_SERVICE_URL:http://localhost:8086}

logging:
  level:
    tz.co.twende: DEBUG
    org.springframework.kafka: WARN
```

---

## 9. Entity Conventions

All entities extend `BaseEntity` from common-lib (provides `id`, `createdAt`, `updatedAt`,
`countryCode`). **Exception:** `UserProfile.id` is NOT auto-generated by `UlidGenerator`.
It is explicitly set from the `UserRegisteredEvent` payload to match auth-service.

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class UserProfile extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String profilePhotoUrl;

    @Column(length = 10)
    private String preferredLocale;

    @Column(length = 30)
    private String preferredPaymentMethod;

    @Column(nullable = false)
    private Boolean isActive = true;
}

@Entity
@Table(name = "saved_places")
@Getter @Setter @NoArgsConstructor
public class SavedPlace extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;
}
```

**Note on `UserProfile.id`:** Override the `@GeneratedValue` behavior from `BaseEntity`.
When creating a profile from the Kafka event, set the ID explicitly before saving:
```java
profile.setId(event.getUserId());  // do NOT let UlidGenerator create a new ID
```

---

## 10. Key Business Rules

1. **User ID = AuthUser ID.** The `users.id` column always matches the `auth_users.id` in
   auth-service. Never generate a new UUID for a user profile.

2. **Only RIDER registrations matter.** The `twende.users.registered` consumer must ignore
   events where `role != RIDER`. Driver profiles are handled by driver-service.

3. **Kafka consumption is idempotent.** Duplicate `UserRegisteredEvent` messages must not
   cause errors or duplicate rows. Always check `existsById` before inserting.

4. **Profile updates publish Kafka events.** Every successful `PUT /api/v1/users/me` must
   publish a `UserProfileUpdatedEvent` to `twende.users.profile-updated`.

5. **Ride history is proxied.** This service does NOT store ride data. It calls ride-service
   via RestClient to fetch the rider's ride history.

6. **No Eureka, no Feign.** All inter-service REST calls use Spring `RestClient` with
   URLs configured via environment variables.

7. **User identity comes from gateway headers.** Always read `X-User-Id` from request
   headers. Never accept user ID from the request body for `/me` endpoints.

8. **Saved places belong to the user.** When deleting a saved place, verify that the
   place's `userId` matches the calling user's ID. Never allow cross-user deletion.

9. **Phone numbers are not stored here.** Phone numbers live in auth-service only. This
   service does not duplicate them.

---

## 11. Testing Strategy

### Unit tests
- `UserService` -- profile CRUD logic, Kafka event publishing
- `SavedPlaceService` -- add/remove places, ownership validation
- `UserRegisteredConsumer` -- idempotency, role filtering
- `UserMapper` -- MapStruct mapping correctness

### Integration tests (Testcontainers)
```java
@SpringBootTest
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

### Test naming
```java
@Test
void givenRegisteredEvent_whenRoleIsRider_thenProfileCreated() { ... }

@Test
void givenDuplicateRegisteredEvent_whenConsumed_thenNoError() { ... }

@Test
void givenValidUpdate_whenProfileUpdated_thenKafkaEventPublished() { ... }

@Test
void givenSavedPlace_whenDeletedByDifferentUser_thenForbidden() { ... }
```

### Coverage
- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw verify`
- Excluded from coverage: entities, DTOs, enums, config classes, `UserServiceApplication`

---

## 12. Build and Run

```bash
# Format code
./mvnw spotless:apply -pl user-service

# Run tests
./mvnw test -pl user-service

# Run tests + coverage check
./mvnw verify -pl user-service

# Build JAR
./mvnw package -pl user-service -DskipTests

# Run locally (requires PostgreSQL + Redis + Kafka)
java -jar user-service/target/user-service-*.jar
```

---

## Charter, Cargo & Flat Fee Expansion (Phase 7-9)

### User Roles in Context (Phase 7)

- Users can act as different roles depending on the service category:
  - **Riders** -- book on-demand rides (`serviceCategory=RIDE`)
  - **Organizers** -- book charter transport (`serviceCategory=CHARTER`)
  - **Shippers** -- book cargo transport (`serviceCategory=CARGO`)
- No schema change needed on `UserProfile` -- the `serviceCategory` is a property of the booking (ride entity), not the user profile
- A single user account can create bookings across all service categories

### No Breaking Changes

- Existing user profile endpoints remain unchanged
- Ride history endpoint (`GET /api/v1/users/me/ride-history`) will include charter and cargo bookings alongside rides -- differentiated by `serviceCategory` in the response
- No new user-service endpoints required for Phase 7-9

---

## Implementation Steps

Work through these in order. Do not skip ahead.

- [ ] **1. application.yml** — Configure port 8083, datasource `twende_users`, Redis connection, Kafka consumer (`user-service-group`, earliest offset, `JsonDeserializer` with trusted packages) and producer (`JsonSerializer`), ride-service URL for RestClient
- [ ] **2. Entities** — Create `UserProfile` (extends `BaseEntity`, maps to `users` table, ID set explicitly from Kafka event not auto-generated) and `SavedPlace` (extends `BaseEntity`, maps to `saved_places` table)
- [ ] **3. Repositories** — Create `UserProfileRepository` and `SavedPlaceRepository` with query methods (`findByUserId` for saved places, `existsById` for idempotency checks)
- [ ] **4. UserService** — Implement `getProfile(UUID)`, `updateProfile(UUID, UpdateProfileRequest)` with Kafka event publishing on update, `getRideHistory()` delegating to `RideServiceClient`
- [ ] **5. SavedPlaceService** — Implement CRUD for saved places with ownership validation (verify `userId` matches calling user before delete)
- [ ] **6. Kafka consumer** — `UserRegisteredConsumer` listening on `twende.users.registered` with group `user-service-group`. Filter for `role == RIDER` only. Idempotent: check `existsById` before inserting. Set `UserProfile.id` from event payload (do not auto-generate)
- [ ] **7. Kafka producer** — `UserProfileUpdatedProducer` publishing `UserProfileUpdatedEvent` to `twende.users.profile-updated` on every successful profile update
- [ ] **8. RideServiceClient** — Spring `RestClient` calling ride-service at configured URL for paginated ride history. Forward `X-User-Id` header. Handle errors gracefully
- [ ] **9. UserController + SavedPlaceController** — `GET/PUT /api/v1/users/me`, `POST /api/v1/users/me/photo`, `GET/POST/DELETE /api/v1/users/me/saved-places`, `GET /api/v1/users/me/ride-history`, admin `GET /api/v1/users/{id}`. Read identity from `X-User-Id` header. All responses wrapped in `ApiResponse<T>`
- [ ] **10. DTOs + MapStruct mapper** — Create `UserProfileDto`, `UpdateProfileRequest` (with validation annotations), `SavedPlaceDto`, `CreateSavedPlaceRequest`, `RideHistoryResponse`, Kafka event DTOs. Create `UserMapper` with MapStruct
- [ ] **11. Flyway migration** — `V1__create_users_schema.sql` with `users` table (PK not auto-generated) and `saved_places` table with index on `user_id`
- [ ] **12. Unit tests + integration tests** — Unit tests for `UserService`, `SavedPlaceService` (ownership validation), `UserRegisteredConsumer` (idempotency, role filtering). Integration tests with Testcontainers (PostgreSQL + Kafka) covering profile CRUD, saved places CRUD, Kafka consumption and publishing
- [ ] **13. Dockerfile** — Multi-stage build (eclipse-temurin:21-jdk-alpine for build, 21-jre-alpine for run). Non-root `twende` user. Health check on `/actuator/health`. Expose port 8083.
- [ ] **14. OpenAPI config** — `OpenApiConfig.java` with SpringDoc `OpenAPI` bean. Title: "User Service API". Swagger UI at `/swagger-ui.html`.
- [ ] **15. Verify build** — Run `./mvnw -pl user-service clean verify` and confirm all tests pass with minimum 80% line coverage
