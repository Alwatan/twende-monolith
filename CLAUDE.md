# CLAUDE.md — Twende Platform

> This file is the single source of truth for Claude Code when building Twende.
> Read it fully before writing any code. Re-read relevant sections before each task.

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

## 2. Architecture: Modular Monolith

Single Spring Boot application. All domains in one codebase. Modules communicate via direct
Java service calls and Spring `ApplicationEventPublisher` (no Kafka for internal events).

**Why monolith first:**
- Faster to build and iterate
- Simpler deployment (single JAR)
- Easier debugging (one process, one log stream)
- Can be decomposed to microservices later by extracting modules along existing boundaries

**Modular boundaries are still enforced:**
- No module imports another module's `repository` or `entity` directly
- Cross-module data access goes through the other module's `Service` interface
- Each module has its own Flyway migration file
- Module packages are clearly separated under `com.twende.modules`

---

## 3. Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 (LTS) | Language |
| Spring Boot | 4.0.5 | Framework |
| Spring Security | (included with Boot 4) | Auth + method security |
| Spring Authorization Server | latest compatible | OAuth2 JWT issuance |
| Spring Data JPA + Hibernate | (included) | ORM |
| Spring Data Redis | (included) | Caching, GEO, rate limiting |
| Spring WebSocket | (included) | Real-time location |
| PostgreSQL | 16 | Primary database |
| Flyway | (included with Boot) | Schema migrations |
| Redis | 7 | Cache, GEO, sessions |
| Lombok | 1.18.34 | Boilerplate reduction |
| MapStruct | 1.6.0 | Entity ↔ DTO mapping |
| SpringDoc OpenAPI | 2.6.0 | API documentation |
| Micrometer + Prometheus | (included) | Metrics |
| Micrometer Tracing + Zipkin | (included) | Distributed tracing |
| Testcontainers | 1.20.0 | Integration tests |
| Africa's Talking SDK | 3.4.11 | SMS (via JitPack) |
| Firebase Admin SDK | 9.3.0 | Push notifications (FCM) |
| MinIO SDK | 8.5.11 | File storage (driver docs) |
| Google Maps Services | 2.2.0 | Distance + routing |
| ULID Creator | 5.2.3 | Time-sortable unique IDs |
| SendGrid | 4.10.2 | Email |

**No Spring Cloud.** No Eureka. No Config Server. No Spring Cloud Gateway.
These are microservices concerns. Not needed here.

---

## 4. Package Structure

```
com.twende
├── TwendeApplication.java
├── config/
│   ├── SecurityConfig.java           # Spring Security + OAuth2 config
│   ├── WebSocketConfig.java          # WebSocket endpoint registration
│   ├── RedisConfig.java              # Redis template beans
│   ├── JpaConfig.java                # @EnableJpaAuditing
│   ├── AsyncConfig.java              # @EnableAsync thread pool
│   └── OpenApiConfig.java            # SpringDoc config
├── common/
│   ├── entity/
│   │   ├── BaseEntity.java           # ULID PK (stored as UUID), createdAt, updatedAt, countryCode
│   │   └── UlidGenerator.java        # Custom Hibernate IdentifierGenerator for ULIDs
│   ├── response/
│   │   ├── ApiResponse.java          # Standard response wrapper
│   │   └── PagedResponse.java        # Paginated wrapper
│   ├── exception/
│   │   ├── TwendeException.java      # Base exception
│   │   ├── ResourceNotFoundException.java
│   │   ├── UnauthorizedException.java
│   │   ├── ConflictException.java
│   │   ├── BadRequestException.java
│   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice
│   ├── enums/
│   │   ├── RideStatus.java
│   │   ├── DriverStatus.java
│   │   ├── VehicleType.java
│   │   ├── PaymentStatus.java
│   │   ├── SubscriptionPlan.java
│   │   ├── SubscriptionStatus.java
│   │   ├── NotificationType.java
│   │   ├── DocumentType.java
│   │   ├── CountryCode.java
│   │   ├── UserRole.java
│   │   └── OfferStatus.java
│   ├── event/
│   │   ├── TwendeEvent.java          # Base Spring ApplicationEvent
│   │   ├── RideRequestedEvent.java
│   │   ├── RideCompletedEvent.java
│   │   ├── DriverMatchedEvent.java
│   │   ├── DriverRejectedRideEvent.java
│   │   ├── RideFareBoostedEvent.java
│   │   ├── TripStartOtpGeneratedEvent.java
│   │   ├── PaymentCompletedEvent.java
│   │   ├── SubscriptionActivatedEvent.java
│   │   ├── SubscriptionExpiredEvent.java
│   │   ├── FreeRideOfferEarnedEvent.java
│   │   └── FreeRideCompletedEvent.java
│   └── util/
│       ├── PhoneUtil.java            # E.164 normalisation
│       ├── CurrencyUtil.java         # Format amounts per country
│       └── OtpUtil.java              # 4-digit OTP generation (SecureRandom)
└── modules/
    ├── auth/
    │   ├── entity/AuthUser.java
    │   ├── entity/OtpCode.java
    │   ├── repository/AuthUserRepository.java
    │   ├── repository/OtpCodeRepository.java
    │   ├── service/AuthService.java
    │   ├── service/OtpService.java
    │   ├── controller/AuthController.java
    │   ├── dto/...
    │   └── config/AuthorizationServerConfig.java
    ├── countryconfig/
    │   ├── entity/CountryConfig.java
    │   ├── entity/VehicleTypeConfig.java
    │   ├── entity/OperatingCity.java
    │   ├── entity/PaymentMethodConfig.java
    │   ├── repository/...
    │   ├── service/CountryConfigService.java
    │   └── controller/CountryConfigController.java
    ├── user/
    │   ├── entity/UserProfile.java
    │   ├── entity/SavedPlace.java
    │   ├── repository/...
    │   ├── service/UserService.java
    │   └── controller/UserController.java
    ├── driver/
    │   ├── entity/DriverProfile.java
    │   ├── entity/DriverVehicle.java
    │   ├── entity/DriverDocument.java
    │   ├── repository/...
    │   ├── service/DriverService.java
    │   ├── service/DocumentService.java
    │   └── controller/DriverController.java
    ├── location/
    │   ├── service/LocationService.java
    │   ├── service/EtaService.java
    │   ├── websocket/LocationWebSocketHandler.java
    │   ├── websocket/WebSocketSessionRegistry.java
    │   └── dto/...
    ├── pricing/
    │   ├── service/PricingService.java
    │   ├── service/SurgeService.java
    │   └── controller/PricingController.java
    ├── matching/
    │   ├── service/MatchingService.java
    │   ├── service/DriverOfferService.java
    │   └── event/...
    ├── ride/
    │   ├── entity/Ride.java
    │   ├── entity/RideStatusEvent.java
    │   ├── entity/RideDriverRejection.java
    │   ├── repository/...
    │   ├── service/RideService.java
    │   ├── service/FareBoostService.java
    │   ├── service/TripOtpService.java
    │   └── controller/RideController.java
    ├── payment/
    │   ├── entity/Transaction.java
    │   ├── entity/DriverWallet.java
    │   ├── entity/WalletEntry.java
    │   ├── repository/...
    │   ├── service/PaymentService.java
    │   ├── service/WalletService.java
    │   ├── provider/PaymentProvider.java
    │   ├── provider/SelcomProvider.java
    │   └── controller/PaymentController.java
    ├── subscription/
    │   ├── entity/Subscription.java
    │   ├── entity/SubscriptionPlan.java
    │   ├── repository/...
    │   ├── service/SubscriptionService.java
    │   └── controller/SubscriptionController.java
    ├── notification/
    │   ├── entity/NotificationLog.java
    │   ├── entity/FcmToken.java
    │   ├── entity/NotificationTemplate.java
    │   ├── repository/...
    │   ├── service/NotificationService.java
    │   ├── service/FcmService.java
    │   ├── service/SmsService.java
    │   ├── service/TemplateResolver.java
    │   └── controller/NotificationController.java
    ├── rating/
    │   ├── entity/Rating.java
    │   ├── repository/...
    │   ├── service/RatingService.java
    │   └── controller/RatingController.java
    ├── analytics/
    │   ├── entity/AnalyticsEvent.java
    │   ├── repository/...
    │   ├── service/AnalyticsService.java
    │   └── controller/AnalyticsController.java
    ├── loyalty/
    │   ├── entity/LoyaltyRule.java
    │   ├── entity/RiderProgress.java
    │   ├── entity/FreeRideOffer.java
    │   ├── repository/...
    │   ├── service/LoyaltyService.java
    │   └── controller/LoyaltyController.java
    └── compliance/
        ├── entity/TripReport.java
        ├── repository/...
        ├── service/ComplianceService.java
        ├── adapter/ComplianceAdapter.java
        └── adapter/SumatraAdapter.java
```

---

## 5. Global Conventions

**These apply to every file in the project. Never deviate.**

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

### BaseEntity (ULID-based primary keys)
ULIDs are time-sortable, globally unique, and stored as standard UUID columns in PostgreSQL.
The Java type remains `UUID` — ULIDs are binary-compatible. The custom `UlidGenerator`
produces monotonically increasing IDs for better B-tree index performance.

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

### UlidGenerator
```java
public class UlidGenerator implements IdentifierGenerator {
    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        return UlidCreator.getMonotonicUlid().toUuid();
    }
}
```

### API Response wrapper — ALWAYS use this
```java
// Every controller method returns ApiResponse<T>
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<RideDto>> getRide(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(rideService.getRide(id)));
}
```

### Money — NEVER use double or float
```java
// Correct
private BigDecimal amount;  // Java field
// DB column: NUMERIC(12,2)

// Wrong — never do this
private double amount;
private float amount;
```

### Timestamps — always UTC, always Instant
```java
private Instant createdAt;    // correct
private LocalDateTime time;   // wrong — no timezone info
private Date date;            // wrong — legacy
```

### Reading the calling user — from security context, not request body
```java
@GetMapping("/me")
public ResponseEntity<ApiResponse<UserDto>> getProfile() {
    UUID userId = SecurityContextHolder.getContext().getAuthentication()
        .getName()  // subject claim = userId
        ...;
}
// Or inject a helper:
@Component
public class CurrentUser {
    public UUID id() { ... }
    public String role() { ... }
    public String countryCode() { ... }
}
```

### Validation — always validate incoming requests
```java
@PostMapping
public ResponseEntity<ApiResponse<...>> create(@Valid @RequestBody CreateRideRequest req) { ... }

public class CreateRideRequest {
    @NotNull private VehicleType vehicleType;
    @NotNull @DecimalMin("0.0") private BigDecimal pickupLat;
    // etc.
}
```

### Module boundary rule
```java
// CORRECT: ride module calls driver service
@Service
public class RideService {
    private final DriverService driverService;  // inject the service, not the repo
    ...
    DriverProfile driver = driverService.findById(driverId);
}

// WRONG: ride module accessing driver repository directly
@Service
public class RideService {
    private final DriverRepository driverRepository;  // cross-module repo access — forbidden
}
```

### Spring Events for cross-module communication
```java
// Publishing an event
applicationEventPublisher.publishEvent(new RideCompletedEvent(this, ride));

// Listening in another module
@Component
public class ComplianceEventListener {
    @EventListener
    @Async  // non-blocking — always use @Async for event listeners
    public void onRideCompleted(RideCompletedEvent event) {
        complianceService.logTrip(event.getRide());
    }
}
```

---

## 6. Security Architecture

### OAuth2 Authorization Server (Spring Authorization Server)
- auth-service issues JWTs signed with an RSA key
- All other controllers are resource servers that validate JWTs
- Token claims include: `sub` (userId UUID), `roles` (list), `countryCode`
- OTP-based login: rider/driver authenticate via phone number + 6-digit SMS OTP
- Refresh token rotation is enabled

### Resource Server (all modules except auth)
```java
// In SecurityConfig.java
http.oauth2ResourceServer(oauth2 ->
    oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
);
```

### Method-level security — use on admin endpoints
```java
@EnableMethodSecurity  // on SecurityConfig or main class
...
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/drivers")
public ResponseEntity<...> getAllDrivers() { ... }
```

### JWT custom claims — add in token customizer
```java
@Bean
public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
    return context -> {
        if (context.getTokenType().equals(OAuth2TokenType.ACCESS_TOKEN)) {
            AuthUser user = authUserRepository.findById(UUID.fromString(context.getPrincipal().getName())).orElseThrow();
            context.getClaims()
                .claim("roles", List.of(user.getRole().name()))
                .claim("countryCode", user.getCountryCode());
        }
    };
}
```

### Endpoint security rules
- `/api/v1/auth/**` and `/oauth2/**` → public
- `/api/v1/config/**` GET → public; PUT/POST/PATCH → ADMIN only
- `/ws/**` → auth via `?token=` query parameter on handshake
- `/actuator/health` → public
- Everything else → authenticated

---

## 7. Database Conventions

### Single database: `twende`
One PostgreSQL database. Tables are prefixed by module where needed to avoid name conflicts.
All Flyway migrations in `src/main/resources/db/migration/`.

### Migration file naming
```
V1__auth_schema.sql
V2__country_config_schema.sql
V3__user_schema.sql
V4__driver_schema.sql
V5__location_schema.sql
V6__ride_schema.sql
V7__payment_schema.sql
V8__subscription_schema.sql
V9__notification_schema.sql
V10__rating_schema.sql
V11__analytics_schema.sql
V12__compliance_schema.sql
V13__loyalty_schema.sql
V14__seed_tanzania.sql
V15__seed_notification_templates.sql
V16__seed_loyalty_rules.sql
```

### Column conventions
```sql
id           UUID         PRIMARY KEY  -- ULID generated by application (UlidGenerator), no DB default needed
country_code CHAR(2)      NOT NULL
created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
-- Money: always NUMERIC(12,2), never FLOAT or DOUBLE PRECISION
amount       NUMERIC(12,2) NOT NULL
-- Enums: VARCHAR with CHECK or PostgreSQL native ENUM
status       VARCHAR(30)  NOT NULL
```

### JPA config: NEVER use ddl-auto=create or update in production
```yaml
spring.jpa.hibernate.ddl-auto: validate  # Flyway manages schema
```

---

## 8. Module Specifications

### Module: auth

**Purpose:** Phone OTP authentication, JWT issuance, token revocation.

**Key entities:**
- `AuthUser(id UUID, phoneNumber VARCHAR(20) UNIQUE, countryCode CHAR(2), role VARCHAR(20), isActive BOOLEAN, phoneVerified BOOLEAN)`
- `OtpCode(id UUID, phoneNumber VARCHAR(20), code VARCHAR(6), expiresAt TIMESTAMPTZ, used BOOLEAN, attempts INT)`

**Key service methods:**
```java
void requestOtp(String phoneNumber, String countryCode);
// Rate limit: max 3 OTP requests per phone per 10 min (Redis counter)
// Generate 6-digit OTP, save to DB with 5-min expiry, send via Africa's Talking

TokenResponse verifyOtp(String phoneNumber, String otp);
// Check OTP: valid, not used, not expired, attempts < 3
// Mark OTP as used
// Issue OAuth2 access + refresh token via programmatic token generation
// Return isNewUser=true if first login

void register(UUID userId, String fullName, UserRole role);
// Called after first OTP verify
// Create AuthUser, publish Spring event → user/driver module creates profile

void logout(String refreshToken);
// Add jti to Redis blocklist (TTL = remaining refresh token lifetime)
```

**OTP rate limiting (Redis):**
```java
String key = "otp:rate:" + phoneNumber;
Long count = redisTemplate.opsForValue().increment(key);
if (count == 1) redisTemplate.expire(key, 10, TimeUnit.MINUTES);
if (count > 3) throw new TooManyRequestsException("Too many OTP requests");
```

**Key endpoint:** `POST /api/v1/auth/otp/request`, `POST /api/v1/auth/otp/verify`,
`POST /api/v1/auth/register`, `POST /api/v1/auth/logout`

---

### Module: countryconfig

**Purpose:** Master configuration for each country. Source of truth for currencies, vehicle
types, pricing, payment methods, regulatory requirements, feature flags.

**Key entities:**
- `CountryConfig(code CHAR(2) PK, name, status, defaultLocale, currencyCode, currencySymbol, minorUnits, phonePrefix, cashEnabled, subscriptionAggregator, regulatoryAuthority, tripReportingRequired, features JSONB)`
- `VehicleTypeConfig(id UUID, countryCode, vehicleType, displayName, maxPassengers, baseFare NUMERIC(12,2), perKm NUMERIC(12,2), perMinute NUMERIC(12,2), minimumFare NUMERIC(12,2), cancellationFee NUMERIC(12,2), surgeMultiplierCap NUMERIC(4,2), isActive BOOLEAN)`
- `OperatingCity(id UUID, countryCode, cityId, name, timezone, status, centerLat, centerLng, radiusKm)`

**Caching:** Cache country config in Redis with 5-minute TTL.
```java
@Cacheable(value = "country-config", key = "#code")
public CountryConfigDto getConfig(String code) { ... }

@CacheEvict(value = "country-config", key = "#code")
public void updateConfig(String code, ...) { ... }
```

Enable caching: `@EnableCaching` on main class or config class.
Configure Redis as cache manager in `RedisConfig`.

**Tanzania seed data:** Insert in `V13__seed_tanzania.sql` (see section 10).

---

### Module: user

**Purpose:** Rider profile, preferences, saved places.

**Key entities:**
- `UserProfile(id UUID — same as AuthUser id, countryCode, fullName, email, profilePhotoUrl, preferredLocale, isActive)`
- `SavedPlace(id UUID, userId UUID, label, address, latitude, longitude)`

**Profile creation:** Listen for `UserRegisteredEvent` from auth module.
```java
@EventListener @Async
public void onUserRegistered(UserRegisteredEvent event) {
    if (event.getRole() == UserRole.RIDER) {
        userRepository.save(new UserProfile(event.getUserId(), event.getFullName(), event.getCountryCode()));
    }
}
```

**Key endpoints:** `GET/PUT /api/v1/users/me`, `GET/POST/DELETE /api/v1/users/me/saved-places`,
`GET /api/v1/users/me/ride-history`

---

### Module: driver

**Purpose:** Driver profile, document verification, vehicle registration, online/offline status.

**Key entities:**
- `DriverProfile(id UUID, countryCode, fullName, email, profilePhotoUrl, status DriverStatus, rejectionReason, approvedAt)`
- `DriverVehicle(id UUID, driverId UUID, vehicleType, make, model, year, plateNumber, color, isActive)`
- `DriverDocument(id UUID, driverId UUID, documentType, fileUrl, status[PENDING/VERIFIED/REJECTED], verifiedAt, expiresAt)`

**DriverStatus enum:** `PENDING_APPROVAL, APPROVED, OFFLINE, ONLINE_AVAILABLE, ONLINE_ON_TRIP, SUSPENDED, REJECTED`

**Go-online validation:**
```java
public void goOnline(UUID driverId) {
    DriverProfile driver = findApproved(driverId);
    // Check active subscription
    boolean hasSubscription = subscriptionService.hasActiveSubscription(driverId);
    if (!hasSubscription) throw new BadRequestException("Purchase a bundle to go online");
    // Check vehicle registered
    if (!hasActiveVehicle(driverId)) throw new BadRequestException("Register a vehicle first");
    driver.setStatus(DriverStatus.ONLINE_AVAILABLE);
    driverRepository.save(driver);
    locationService.addDriverToGeoIndex(driverId, driver.getCountryCode(), driver.getActiveVehicleType());
}
```

**Document upload:** Files go to MinIO. Store only the URL in `DriverDocument.fileUrl`.

**Key endpoints:** `GET/PUT /api/v1/drivers/me`, `PUT /api/v1/drivers/me/status`,
`POST /api/v1/drivers/me/documents`, `POST /api/v1/drivers/me/vehicles`,
`GET/PUT /api/v1/drivers/{id}/approval` (ADMIN)

---

### Module: location

**Purpose:** Real-time driver location via WebSocket. Redis GEO for nearby-driver queries.
Stores completed trip traces in PostgreSQL.

**WebSocket:** `ws://host/ws/location?token={jwt}` — validate JWT in `HandshakeInterceptor`.

**Driver location update message (driver → server):**
```json
{ "type": "LOCATION_UPDATE", "lat": -6.79, "lng": 39.21, "bearing": 45, "speed": 32 }
```

**Redis operations:**
```java
// Add/update driver position
redisTemplate.opsForGeo().add(
    "drivers:" + countryCode + ":" + vehicleType,
    new Point(lng, lat), driverId.toString()
);

// Set driver detail hash (TTL 90s — stale if no updates)
redisTemplate.opsForHash().putAll("driver:location:" + driverId, Map.of(...));
redisTemplate.expire("driver:location:" + driverId, 90, TimeUnit.SECONDS);

// Find nearby drivers
List<GeoResult<RedisGeoCommands.GeoLocation<String>>> nearby =
    redisTemplate.opsForGeo().radius(
        "drivers:" + countryCode + ":" + vehicleType,
        new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS)),
        GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending()
    );
```

**Rider location push:** When a ride is active, server pushes driver location to the rider's
WebSocket session every time the driver sends an update. Use `WebSocketSessionRegistry`
(a `ConcurrentHashMap<UUID, WebSocketSession>`) to find the rider's session.

**Trip trace:** During `IN_PROGRESS`, append each location point to
`RPUSH ride:trace:{rideId}`. When ride completes, flush trace to `trip_traces` PostgreSQL table.

---

### Module: pricing

**Purpose:** Fare estimation and final fare calculation.

**Fare formula:**
```java
BigDecimal fare = baseFare
    .add(distanceKm.multiply(perKm))
    .add(durationMinutes.multiply(perMinute))
    .multiply(surgeMultiplier);

return fare.max(minimumFare).setScale(0, RoundingMode.HALF_UP);
// Tanzania uses TZS — minorUnits = 0, so round to whole shillings
```

**Surge:** Redis key `surge:{countryCode}:{vehicleType}` (float, updated every 60s by scheduler).
Surge = min(activeRequests / availableDrivers, surgeMultiplierCap). Only applied if
`countryConfig.features.surgeEnabled = true`.

**For estimates:** Call Google Maps Distance Matrix API for distance and duration.
**For final fare:** Use actual `distanceMetres` and `durationSeconds` from the ride record
(populated from trip trace on completion).

**Key endpoints:** `POST /api/v1/pricing/estimate`, `POST /api/v1/pricing/calculate`

---

### Module: matching

**Purpose:** Broadcast-and-accept matching. Find nearby drivers, send offers, handle the
accept/reject race using Redis atomic operations.

**Broadcast flow:**
```java
public void broadcastOffer(Ride ride) {
    List<String> candidates = locationService.findNearbyDrivers(
        ride.getCountryCode(), ride.getVehicleType(),
        ride.getPickupLat(), ride.getPickupLng(), 3.0);

    for (String driverId : candidates) {
        // Deduplicate — don't offer same ride twice to same driver
        Boolean isNew = redisTemplate.opsForValue()
            .setIfAbsent("driver_offered:" + driverId + ":" + ride.getId(), "1",
                Duration.ofSeconds(120));
        if (Boolean.TRUE.equals(isNew)) {
            redisTemplate.opsForSet().add("rides_offered_to:" + ride.getId(), driverId);
            redisTemplate.expire("rides_offered_to:" + ride.getId(), 300, TimeUnit.SECONDS);
            notificationService.sendDriverOffer(UUID.fromString(driverId), ride);
        }
    }
}
```

**Accept race (atomic):**
```java
public boolean tryAcceptRide(UUID rideId, UUID driverId) {
    Boolean won = redisTemplate.opsForValue()
        .setIfAbsent("ride_accepted:" + rideId, driverId.toString(), Duration.ofSeconds(60));
    return Boolean.TRUE.equals(won);
}
```

**Driver reject:**
```java
public void handleDriverReject(UUID rideId, UUID driverId) {
    redisTemplate.opsForSet().add("driver_rejected:" + rideId, driverId.toString());
    redisTemplate.expire("driver_rejected:" + rideId, 300, TimeUnit.SECONDS);
    applicationEventPublisher.publishEvent(new DriverRejectedRideEvent(this, rideId, driverId));
}
```

**Expansion scheduler** (runs every 30s):
```java
@Scheduled(fixedDelay = 30_000)
public void expandMatchingRadius() {
    // Query rides in REQUESTED status from rideService
    // For each ride, check age and expand radius:
    // < 60s → 3km (already done by initial broadcast)
    // 60–120s → 5km batch
    // 120–180s → 10km batch
    // > 180s → publish NoDriverFoundEvent via applicationEventPublisher
}
```

**Key endpoints (driver-facing, called via RideController):**
`POST /api/v1/rides/{id}/accept` — driver accepts an offer
`POST /api/v1/rides/{id}/reject` — driver explicitly rejects

---

### Module: ride

**Purpose:** Core ride lifecycle. Orchestrates matching, pricing, payment.

**RideStatus state machine:**
```
REQUESTED → DRIVER_ASSIGNED → DRIVER_ARRIVED → IN_PROGRESS → COMPLETED
     ↓              ↓
CANCELLED    CANCELLED (after assignment)
     ↓
NO_DRIVER_FOUND
```

**Key entity fields:**
```java
private UUID riderId;
private UUID driverId;             // null until matched
private RideStatus status;
private VehicleType vehicleType;
private BigDecimal pickupLat, pickupLng;
private String pickupAddress;
private BigDecimal dropoffLat, dropoffLng;
private String dropoffAddress;
private BigDecimal estimatedFare;
private BigDecimal fareBoostAmount;   // NEW: 0 if no boost
private BigDecimal finalFare;
private String currencyCode;
private boolean freeRide;              // true if loyalty offer applied
private UUID freeRideOfferId;          // FK to free_ride_offers, null if not free
private int driverRejectionCount;     // NEW: incremented on each driver reject
private String tripStartOtpHash;      // NEW: bcrypt hash of 4-digit code
private Instant tripStartOtpExpiresAt; // NEW
private int tripStartOtpAttempts;      // NEW: max 3
private Integer distanceMetres;
private Integer durationSeconds;
private Instant requestedAt, assignedAt, arrivedAt, startedAt, completedAt, cancelledAt;
private String cancelReason;
private String cancelledBy;            // RIDER / DRIVER / SYSTEM
private Instant matchingTimeoutAt;     // = requestedAt + 3 min
```

**Fare boost validation:**
```java
public void boostFare(UUID rideId, UUID riderId, BigDecimal boostAmount) {
    Ride ride = findByIdAndRider(rideId, riderId);
    if (ride.getStatus() != RideStatus.REQUESTED)
        throw new BadRequestException("Can only boost fare before a driver is assigned");
    if (boostAmount.compareTo(BigDecimal.ZERO) <= 0)
        throw new BadRequestException("Boost amount must be positive");

    VehicleTypeConfig vtc = countryConfigService.getVehicleTypeConfig(
        ride.getCountryCode(), ride.getVehicleType());
    BigDecimal maxFare = vtc.getBaseFare().multiply(vtc.getSurgeMultiplierCap());
    BigDecimal newFare = ride.getEstimatedFare().add(boostAmount);
    if (newFare.compareTo(maxFare) > 0)
        throw new BadRequestException("Fare cannot exceed maximum of " + maxFare);

    ride.setFareBoostAmount(ride.getFareBoostAmount().add(boostAmount));
    ride.setEstimatedFare(newFare);
    rideRepository.save(ride);
    applicationEventPublisher.publishEvent(new RideFareBoostedEvent(this, ride));
    matchingService.rebroadcastOffer(ride);  // re-offer to un-offered drivers
}
```

**OTP generation (on DRIVER_ARRIVED):**
```java
private void generateTripOtp(Ride ride) {
    String otp = OtpUtil.generate4Digit();
    ride.setTripStartOtpHash(passwordEncoder.encode(otp));
    ride.setTripStartOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    ride.setTripStartOtpAttempts(0);
    rideRepository.save(ride);
    applicationEventPublisher.publishEvent(new TripStartOtpGeneratedEvent(this, ride, otp));
    // Notification module listens to this event and sends push to rider
}
```

**OTP verification (start trip):**
```java
public void startTripWithOtp(UUID rideId, UUID driverId, String otp) {
    Ride ride = findByIdAndDriver(rideId, driverId);
    if (ride.getStatus() != RideStatus.DRIVER_ARRIVED)
        throw new BadRequestException("Driver must be marked as arrived first");
    if (ride.getTripStartOtpExpiresAt().isBefore(Instant.now())) {
        regenerateAndSendOtp(ride);
        throw new BadRequestException("Code expired. A new code was sent to the rider.");
    }
    if (!passwordEncoder.matches(otp, ride.getTripStartOtpHash())) {
        ride.setTripStartOtpAttempts(ride.getTripStartOtpAttempts() + 1);
        if (ride.getTripStartOtpAttempts() >= 3) {
            regenerateAndSendOtp(ride);
            throw new BadRequestException("Too many attempts. A new code was sent to the rider.");
        }
        throw new BadRequestException("Wrong code. " + (3 - ride.getTripStartOtpAttempts()) + " attempt(s) left.");
    }
    ride.setStatus(RideStatus.IN_PROGRESS);
    ride.setStartedAt(Instant.now());
    ride.setTripStartOtpHash(null);  // single use
    rideRepository.save(ride);
}
```

**Rejection counter:** Listen for `DriverRejectedRideEvent`:
```java
@EventListener @Async
public void onDriverRejected(DriverRejectedRideEvent event) {
    Ride ride = rideRepository.findById(event.getRideId()).orElseThrow();
    ride.setDriverRejectionCount(ride.getDriverRejectionCount() + 1);
    rideDriverRejectionRepository.save(new RideDriverRejection(ride.getId(), event.getDriverId()));
    rideRepository.save(ride);
    // Notify rider of updated count via notification service
    notificationService.sendRejectionCountUpdate(ride.getRiderId(), ride.getDriverRejectionCount(), ride.getId());
    // At 3 rejections, suggest fare boost
    if (ride.getDriverRejectionCount() == 3) {
        notificationService.sendFareBoostNudge(ride.getRiderId(), ride.getId());
    }
}
```

**Key endpoints:**
```
POST   /api/v1/rides                       Create ride (rider)
GET    /api/v1/rides/{id}                  Get ride status
PUT    /api/v1/rides/{id}/boost            Boost fare (rider)
DELETE /api/v1/rides/{id}                  Cancel ride (rider)
GET    /api/v1/rides/history               Ride history (rider)
POST   /api/v1/rides/{id}/accept           Accept offer (driver)
POST   /api/v1/rides/{id}/reject           Reject offer (driver)
PUT    /api/v1/rides/{id}/arrived          Mark as arrived (driver)
POST   /api/v1/rides/{id}/start            Start trip with OTP (driver)
PUT    /api/v1/rides/{id}/complete         Complete trip (driver)
POST   /api/v1/rides/{id}/otp/resend       Resend OTP to rider
GET    /api/v1/rides/current               Driver's current ride
```

---

### Module: payment

**Purpose:** Driver wallet management, subscription payments (Selcom mobile money),
and driver payouts. Riders pay cash only — no rider-side payment processing.

**Payment flows:**
- **Rider → Driver (normal ride):** Cash at end of trip. No digital processing needed.
- **Rider → Driver (free loyalty ride):** Twende credits driver wallet automatically on trip completion.
- **Driver → Twende (subscription):** Selcom mobile money push-pay.
- **Driver ← Twende (payout):** Selcom disburse from driver wallet to mobile money.

**Provider pattern (for driver-side payments only):**
```java
public interface PaymentProvider {
    String getId();  // "selcom"
    PaymentResult charge(ChargeRequest request);   // subscription purchase
    PaymentResult disburse(DisburseRequest request); // wallet payout
}
```

**Wallet update must be transactional:**
```java
@Transactional
public void creditDriverWallet(UUID driverId, BigDecimal amount, String description) {
    DriverWallet wallet = walletRepository.findByDriverId(driverId)
        .orElseGet(() -> new DriverWallet(driverId));
    wallet.setBalance(wallet.getBalance().add(amount));
    BigDecimal newBalance = wallet.getBalance();
    walletRepository.save(wallet);
    walletEntryRepository.save(new WalletEntry(driverId, "CREDIT", amount, newBalance, description));
}
```

**Event listener for ride completion:**
```java
@EventListener @Async
public void onRideCompleted(RideCompletedEvent event) {
    Ride ride = event.getRide();
    if (ride.isFreeRide()) {
        // Loyalty ride — Twende pays the driver
        walletService.creditDriverWallet(ride.getDriverId(), ride.getFinalFare(),
            "Twende loyalty ride payout — ride " + ride.getId());
    }
    // Cash rides: no wallet action needed, driver already has the cash
}
```

**Key endpoints:** `GET /api/v1/payments/wallet`, `GET /api/v1/payments/earnings`,
`POST /api/v1/payments/withdraw`, `GET /api/v1/payments/history`

---

### Module: subscription

**Purpose:** Driver subscription bundles. Blocks driver from going online without active bundle.

**Plans (Tanzania seed data):**
```sql
('TZ','DAILY',  2000, 'TZS', 24,  'Pakiti ya Siku')
('TZ','WEEKLY', 10000,'TZS', 168, 'Pakiti ya Wiki')
('TZ','MONTHLY',35000,'TZS', 720, 'Pakiti ya Mwezi')
```

**Public check (used by driver module):**
```java
public boolean hasActiveSubscription(UUID driverId) {
    return subscriptionRepository.existsByDriverIdAndStatusAndExpiresAtAfter(
        driverId, SubscriptionStatus.ACTIVE, Instant.now());
}
```

**Expiry scheduler:**
```java
@Scheduled(fixedDelay = 600_000)  // every 10 minutes
public void expireSubscriptions() { ... }
```

---

### Module: notification

**Purpose:** Push (FCM), SMS (Africa's Talking), in-app, email. Event-driven — no outbound
REST API called by other modules. Other modules publish Spring events; this module listens.

**Template resolution:** Resolve by `templateKey + locale` (fall back to `en`).

**Key event listeners:**
```java
@EventListener @Async
public void onTripOtpGenerated(TripStartOtpGeneratedEvent event) {
    // Send push to rider with OTP code in FCM data payload
    fcmService.sendData(event.getRide().getRiderId(), Map.of(
        "type", "TRIP_OTP",
        "otp", event.getOtp(),         // IMPORTANT: in data payload, not notification body
        "rideId", event.getRide().getId().toString()
    ));
}
```

**FCM data vs notification payload — important distinction:**
- Use `notification` payload for messages shown when app is in background
- Use `data` payload for messages the app handles programmatically (OTP display, live updates)
- For OTP and rejection counter updates: `data` only (app handles rendering)
- For "driver on the way" etc.: `notification` payload so it shows on lock screen

---

### Module: loyalty

**Purpose:** Rider loyalty programme. Track ride count and mileage per vehicle type.
Award free ride offers when thresholds are met. Automatically pay drivers for free rides
from Twende's funds via wallet credit.

**Key entities:**
- `LoyaltyRule(id UUID, countryCode, vehicleType, requiredRides INT, requiredDistanceKm NUMERIC(10,2), freeRideMaxDistanceKm NUMERIC(10,2), offerValidityDays INT, isActive BOOLEAN)`
  — Admin-configurable per country + vehicle type. Example: "After 20 Bajaj rides totalling 100+ km, earn a free Bajaj ride up to 5 km, valid for 30 days."
- `RiderProgress(id UUID, riderId UUID, countryCode, vehicleType, rideCount INT, totalDistanceKm NUMERIC(10,2), lastResetAt TIMESTAMPTZ)`
  — Running tally per rider per vehicle type. Resets when a free ride offer is earned.
- `FreeRideOffer(id UUID, riderId UUID, countryCode, vehicleType, maxDistanceKm NUMERIC(10,2), status OfferStatus, earnedAt TIMESTAMPTZ, expiresAt TIMESTAMPTZ, redeemedAt TIMESTAMPTZ, rideId UUID)`
  — The actual offer. One offer per threshold reached. Can be redeemed on exactly one ride.

**OfferStatus enum:** `AVAILABLE, REDEEMED, EXPIRED`

**Progress tracking (event-driven):**
```java
@EventListener @Async
public void onRideCompleted(RideCompletedEvent event) {
    Ride ride = event.getRide();
    if (ride.isFreeRide()) return;  // don't count free rides toward next offer

    RiderProgress progress = progressRepository
        .findByRiderIdAndCountryCodeAndVehicleType(ride.getRiderId(), ride.getCountryCode(), ride.getVehicleType())
        .orElseGet(() -> new RiderProgress(ride.getRiderId(), ride.getCountryCode(), ride.getVehicleType()));

    progress.setRideCount(progress.getRideCount() + 1);
    BigDecimal tripKm = new BigDecimal(ride.getDistanceMetres()).divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
    progress.setTotalDistanceKm(progress.getTotalDistanceKm().add(tripKm));
    progressRepository.save(progress);

    // Check if threshold reached
    LoyaltyRule rule = ruleRepository
        .findByCountryCodeAndVehicleTypeAndIsActiveTrue(ride.getCountryCode(), ride.getVehicleType())
        .orElse(null);
    if (rule != null
        && progress.getRideCount() >= rule.getRequiredRides()
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

    applicationEventPublisher.publishEvent(new FreeRideOfferEarnedEvent(this, offer));
    // Notification module sends push to rider
}
```

**Free ride redemption (called from RideService during ride creation):**
```java
public FreeRideOffer findApplicableOffer(UUID riderId, String countryCode, VehicleType vehicleType, BigDecimal estimatedDistanceKm) {
    return offerRepository
        .findFirstByRiderIdAndCountryCodeAndVehicleTypeAndStatusAndExpiresAtAfterOrderByEarnedAtAsc(
            riderId, countryCode, vehicleType, OfferStatus.AVAILABLE, Instant.now())
        .filter(offer -> estimatedDistanceKm.compareTo(offer.getMaxDistanceKm()) <= 0)
        .orElse(null);
}

@Transactional
public void redeemOffer(UUID offerId, UUID rideId) {
    FreeRideOffer offer = offerRepository.findById(offerId).orElseThrow();
    offer.setStatus(OfferStatus.REDEEMED);
    offer.setRedeemedAt(Instant.now());
    offer.setRideId(rideId);
    offerRepository.save(offer);
}
```

**Ride entity integration:** Add `boolean freeRide` and `UUID freeRideOfferId` fields to
the `Ride` entity. When creating a ride, RideService checks `loyaltyService.findApplicableOffer()`.
If an offer matches, mark the ride as free and redeem the offer. The rider sees fare = 0.
On trip completion, `PaymentService` detects `ride.isFreeRide()` and credits the driver wallet
with the calculated fare amount (Twende absorbs the cost).

**Expiry scheduler:**
```java
@Scheduled(fixedDelay = 3_600_000)  // every hour
public void expireOffers() {
    List<FreeRideOffer> expired = offerRepository
        .findByStatusAndExpiresAtBefore(OfferStatus.AVAILABLE, Instant.now());
    expired.forEach(o -> o.setStatus(OfferStatus.EXPIRED));
    offerRepository.saveAll(expired);
}
```

**Key endpoints:**
```
GET    /api/v1/loyalty/progress              Rider's progress per vehicle type
GET    /api/v1/loyalty/offers                Rider's available free ride offers
GET    /api/v1/loyalty/rules                 Public — show loyalty rules per country
PUT    /api/v1/loyalty/rules/{id}            Admin — update loyalty rule thresholds
```

---

### Modules: rating, analytics, compliance

These are simpler event-driven listeners. Build them last.

**Rating:** `POST /api/v1/ratings` — one rating per ride per role. Rider rates driver and vice
versa. Cache average rating in Redis: `rating:driver:{id}` (TTL 1h).

**Analytics:** `@EventListener @Async` on all significant events. Append to `analytics_events`
(append-only table). Build materialized summaries with a nightly `@Scheduled` job.

**Compliance:** `@EventListener @Async` on `RideCompletedEvent`. Create `trip_reports` record.
`@Scheduled` job attempts SUMATRA submission every hour for unsubmitted records.
Use `ComplianceAdapter` interface with `SumatraAdapter` for Tanzania.

---

## 9. Build Phases

Work through these phases in order. Do not start Phase N+1 until Phase N is complete and
all tests are passing.

**Phase completion rule — applies to EVERY phase:**
1. Implement all items listed for the phase
2. Write unit tests and integration tests covering all new code
3. Run tests: `./mvnw test`
4. Check coverage: `./mvnw verify` (JaCoCo report at `target/site/jacoco/index.html`)
5. **Minimum 80% line coverage** on all new code in the phase. If below 80%, write
   additional tests until the threshold is met. Re-run and confirm all tests pass.
6. Once all tests pass with ≥80% coverage, create a git commit and push to GitHub:
   - Commit message format: `feat(phase-N): <short description of what was built>`
   - Push to remote: `git push origin <branch>`
7. Only then proceed to the next phase.

### Phase 1 — Foundation (build this first, everything depends on it)
- [ ] `pom.xml` with all dependencies (including JaCoCo plugin)
- [ ] `TwendeApplication.java` — `@SpringBootApplication @EnableJpaAuditing @EnableCaching @EnableAsync @EnableScheduling`
- [ ] `common/entity/BaseEntity.java` + `UlidGenerator.java`
- [ ] `common/response/ApiResponse.java` and `PagedResponse.java`
- [ ] `common/exception/` — all exception classes + `GlobalExceptionHandler`
- [ ] `common/enums/` — all enums
- [ ] `common/event/TwendeEvent.java` and all event classes
- [ ] `common/util/PhoneUtil.java`, `CurrencyUtil.java`, `OtpUtil.java`
- [ ] `config/JpaConfig.java` — `@EnableJpaAuditing`
- [ ] `config/AsyncConfig.java` — thread pool for `@Async`
- [ ] `config/RedisConfig.java` — `RedisTemplate<String, Object>` + cache manager
- [ ] `application.yml` — DB, Redis, actuator, Flyway, logging
- [ ] `V1__auth_schema.sql` through `V16__seed.sql` — all migrations
- [ ] Tests, ≥80% coverage, commit & push

### Phase 2 — Security + Country Config
- [ ] `config/SecurityConfig.java` — full OAuth2 config (both auth server and resource server)
- [ ] `modules/auth/` — complete auth module with OTP and token issuance
- [ ] `modules/countryconfig/` — complete with Redis caching + Tanzania seed data
- [ ] `config/OpenApiConfig.java` — SpringDoc with bearer auth
- [ ] Tests, ≥80% coverage, commit & push

### Phase 3 — Identity Modules
- [ ] `modules/user/` — profile creation on `UserRegisteredEvent`, CRUD endpoints
- [ ] `modules/driver/` — profile, documents, vehicles, go-online validation
- [ ] Tests, ≥80% coverage, commit & push

### Phase 4 — Ride Flow
- [ ] `modules/location/` — WebSocket handler, Redis GEO operations, session registry
- [ ] `modules/pricing/` — fare calculation, surge (static multiplier in Phase 4, dynamic in Phase 6)
- [ ] `modules/matching/` — broadcast-and-accept, expansion scheduler
- [ ] `modules/ride/` — full lifecycle, fare boost, OTP, rejection counter
- [ ] Tests, ≥80% coverage, commit & push

### Phase 5 — Commerce
- [ ] `modules/payment/` — Selcom for subscriptions + payouts, wallet management, free ride wallet credit
- [ ] `modules/subscription/` — plans, purchase via Selcom, expiry scheduler
- [ ] `modules/loyalty/` — rules, progress tracking, free ride offers, expiry scheduler
- [ ] Tests, ≥80% coverage, commit & push

### Phase 6 — Supporting Features
- [ ] `modules/notification/` — FCM, SMS, template system, event listeners
- [ ] `modules/rating/` — submit and aggregate ratings
- [ ] Dynamic surge pricing in pricing module (scheduler + Redis)
- [ ] Tests, ≥80% coverage, commit & push

### Phase 7 — Admin and Observability
- [ ] `modules/analytics/` — event ingestion, earnings dashboard
- [ ] `modules/compliance/` — SUMATRA adapter, trip report batch submission
- [ ] Admin endpoints across all modules (`@PreAuthorize("hasRole('ADMIN')")`)
- [ ] Prometheus metrics exposed at `/actuator/prometheus`
- [ ] Zipkin tracing configured
- [ ] Tests, ≥80% coverage, commit & push

---

## 10. Testing Strategy

### Unit tests — for pure logic
- All `util/` classes
- `PricingService.calculateFare(...)` — test all edge cases (minimum fare, surge cap)
- `TripOtpService` — expiry, attempts, single-use
- `MatchingService.scoreCandidate(...)` — scoring algorithm
- State machine transitions in `RideService`

### Integration tests — use Testcontainers
```java
@SpringBootTest
@Testcontainers
class RideFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // Test: create ride, boost fare, simulate accept, verify OTP, complete
}
```

### Test naming convention
```java
// Given_When_Then pattern
@Test
void givenRideInRequestedStatus_whenRiderBoostsFare_thenFareUpdatedAndRebroadcastTriggered() { ... }

@Test
void givenDriverArrivedStatus_whenDriverEntersCorrectOtp_thenRideMovesToInProgress() { ... }

@Test
void givenThreeWrongOtpAttempts_whenDriverEntersFourthAttempt_thenNewOtpGeneratedAndSentToRider() { ... }
```

### Coverage enforcement
- JaCoCo plugin is configured in `pom.xml` with 80% minimum line coverage
- `./mvnw verify` runs tests AND enforces coverage — build fails if below 80%
- Coverage report: `target/site/jacoco/index.html`
- Excluded from coverage: entities, DTOs, enums, config classes, `TwendeApplication`

---

## 11. CI/CD Pipeline

GitHub Actions pipeline at `.github/workflows/ci.yml`. Runs on every push to `main`/`develop`
and on all pull requests targeting those branches.

### Pipeline stages (in order)

| Stage | What it does | Blocks build? |
|---|---|---|
| **Lint & Format** | `spotless:check` — enforces consistent code style (Google Java Format, AOSP) | Yes |
| **Dependency Scan** | OWASP Dependency-Check — fails on CVE score ≥ 7 | Yes |
| **SAST** | GitHub CodeQL — static analysis for security vulnerabilities (SQL injection, XSS, etc.) | Yes |
| **Test & Coverage** | `./mvnw verify` with Postgres + Redis services — runs all tests, enforces ≥80% JaCoCo coverage | Yes |
| **Build** | `./mvnw package` — produces JAR artifact | Yes (needs lint + test) |
| **Container Build & Scan** | Builds Docker image, scans with Trivy for CRITICAL/HIGH CVEs | Yes (push only) |
| **Publish** | Pushes image to GitHub Container Registry (`ghcr.io`) | main branch only |
| **Deploy** | Placeholder — commented out until dev server is provisioned | — |

### Key design decisions
- **Security scanning at 3 levels:** dependencies (OWASP), source code (CodeQL), container image (Trivy)
- **Fail fast:** lint and format run first since they're fastest
- **Scan results** uploaded to GitHub Security tab (SARIF format) for tracking
- **Artifacts retained:** JaCoCo report (14 days), dependency-check report (14 days), JAR (7 days)
- **Container runs as non-root** `twende` user with health checks
- **No secrets in image:** all config via environment variables at runtime

### Formatting
Run `./mvnw spotless:apply` to auto-fix formatting before committing.
The Makefile `format` target does the same: `make format`.

### When deploy is ready
Uncomment the `deploy-dev` job in `ci.yml` and configure:
- GitHub Environment `dev` with required reviewers (optional)
- Secrets: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` (or cloud credentials)
- Choose deployment method: SSH + Docker Compose, Kubernetes, AWS ECS, or Kamal

---

## 12. External Integrations

### Africa's Talking (SMS + OTP)
```java
AfricasTalking.initialize(username, apiKey);
SMSService smsService = AfricasTalking.getApplication().getSmsService();
smsService.send("Your code: " + otp, new String[]{phoneNumber}, "TWENDE");
```
In dev: set `twende.sms.dev-mode=true` to log OTP to console instead of sending.

### Firebase Cloud Messaging (push)
```java
FirebaseApp.initializeApp(FirebaseOptions.builder()
    .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes())))
    .build());

Message message = Message.builder()
    .putAllData(dataMap)          // data payload — for OTP, live updates
    .setToken(fcmToken)
    .build();
FirebaseMessaging.getInstance().send(message);
```

### Google Maps (distance + ETA)
```java
GeoApiContext context = new GeoApiContext.Builder().apiKey(apiKey).build();
DistanceMatrixApiRequest req = DistanceMatrixApi.newRequest(context)
    .origins(new com.google.maps.model.LatLng(pickupLat, pickupLng))
    .destinations(new com.google.maps.model.LatLng(dropoffLat, dropoffLng))
    .mode(TravelMode.DRIVING);
DistanceMatrix result = req.await();
long metres = result.rows[0].elements[0].distance.inMeters;
long seconds = result.rows[0].elements[0].duration.inSeconds;
```
Wrap in `@Cacheable` keyed on rounded coordinates (to 3dp) — Google Maps is expensive.

### MinIO (document storage)
```java
minioClient.uploadObject(UploadObjectArgs.builder()
    .bucket("twende-driver-documents")
    .object(driverId + "/" + documentType + "/" + filename)
    .filename(localPath)
    .contentType(contentType)
    .build());
String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
    .method(Method.GET)
    .bucket("twende-driver-documents")
    .object(objectName)
    .expiry(7, TimeUnit.DAYS)
    .build());
```

### Selcom Tanzania (mobile money)
Use the Selcom API. Key endpoints: `push-pay` for driver subscription purchase,
`disburse` for driver wallet payouts. Always store `providerRef` from response for reconciliation.
Wrap all Selcom calls in a try-catch with transaction status management:
```java
try {
    SelcomResponse response = selcomClient.charge(request);
    transaction.setStatus(COMPLETED);
    transaction.setProviderRef(response.getReference());
} catch (SelcomException e) {
    transaction.setStatus(FAILED);
    transaction.setFailureReason(e.getMessage());
    // Retry via @Scheduled job for PROCESSING → FAILED upgrades
}
```

---

## 13. Application Configuration

### application.yml
```yaml
server:
  port: 8080

spring:
  application:
    name: twende
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
    hikari:
      maximum-pool-size: 20
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
  security:
    oauth2:
      authorizationserver:
        issuer-uri: ${AUTH_ISSUER_URI:http://localhost:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0

twende:
  sms:
    dev-mode: ${SMS_DEV_MODE:true}
  maps:
    api-key: ${GOOGLE_MAPS_API_KEY:}
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:twende}
    secret-key: ${MINIO_SECRET_KEY:twende123}
  selcom:
    api-key: ${SELCOM_API_KEY:}
    api-secret: ${SELCOM_API_SECRET:}
  firebase:
    service-account-json: ${FIREBASE_SERVICE_ACCOUNT_JSON:}
  africastalking:
    api-key: ${AT_API_KEY:}
    username: ${AT_USERNAME:sandbox}

logging:
  level:
    com.twende: DEBUG
    org.springframework.security: WARN
```

---

## 14. Important Business Rules (Never Override These)

1. **Driver keeps 100%** — ride fare goes entirely to driver wallet. Twende earns from
   subscription bundles only. Never deduct a percentage from the ride payment to Twende.

2. **No subscription = no online** — a driver with an expired subscription cannot set
   status to `ONLINE_AVAILABLE`. Hard block in `DriverService.goOnline()`.

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
