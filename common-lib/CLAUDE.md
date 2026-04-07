# common-lib

## Overview

`common-lib` is a shared Java library (plain JAR, **not** a Spring Boot executable) imported
by all Twende microservices. It provides:

- ULID-based JPA entity with audit fields
- Standard API response wrappers
- Common enums used across services
- Kafka event schemas (all event POJOs)
- Custom exception hierarchy + auto-configured global exception handler
- Utility classes (phone formatting, currency formatting, OTP generation, pagination)

**GroupId:** `com.twende`
**ArtifactId:** `common-lib`
**Packaging:** `jar` (plain JAR -- do NOT include `spring-boot-maven-plugin`)

**Not used:** Eureka, Feign, Spring Cloud. These are not part of this platform.

---

## Package Structure

```
com.twende.common
├── config/
│   └── CommonAutoConfiguration.java       # Spring Boot auto-config (registers GlobalExceptionHandler)
├── entity/
│   ├── BaseEntity.java                    # ULID PK (stored as UUID) + audit timestamps + countryCode
│   └── UlidGenerator.java                 # Custom Hibernate IdentifierGenerator for ULIDs
├── response/
│   ├── ApiResponse.java                   # Standard response wrapper
│   └── PagedResponse.java                # Paginated response wrapper
├── exception/
│   ├── TwendeException.java              # Base exception
│   ├── ResourceNotFoundException.java
│   ├── UnauthorizedException.java
│   ├── ConflictException.java
│   ├── BadRequestException.java
│   └── GlobalExceptionHandler.java       # @RestControllerAdvice
├── enums/
│   ├── RideStatus.java
│   ├── DriverStatus.java
│   ├── VehicleType.java
│   ├── PaymentStatus.java
│   ├── PaymentMethod.java
│   ├── SubscriptionPlan.java
│   ├── SubscriptionStatus.java
│   ├── NotificationType.java
│   ├── DocumentType.java
│   ├── CountryCode.java
│   ├── UserRole.java
│   ├── DriverOfferAction.java
│   └── OfferStatus.java
├── event/
│   ├── KafkaEvent.java                    # Base event class
│   ├── ride/
│   │   ├── RideRequestedEvent.java
│   │   ├── RideStatusUpdatedEvent.java
│   │   ├── RideCompletedEvent.java
│   │   └── RideFareBoostedEvent.java
│   ├── driver/
│   │   ├── DriverMatchedEvent.java
│   │   ├── DriverStatusUpdatedEvent.java
│   │   ├── DriverRejectedRideEvent.java
│   │   ├── DriverOfferNotificationEvent.java
│   │   └── RideOfferAcceptedEvent.java
│   ├── payment/
│   │   ├── PaymentInitiatedEvent.java
│   │   └── PaymentCompletedEvent.java
│   ├── subscription/
│   │   ├── SubscriptionActivatedEvent.java
│   │   └── SubscriptionExpiredEvent.java
│   ├── user/
│   │   └── UserRegisteredEvent.java
│   ├── notification/
│   │   └── SendNotificationEvent.java
│   └── loyalty/
│       ├── FreeRideOfferEarnedEvent.java
│       └── FreeRideCompletedEvent.java
└── util/
    ├── PhoneUtil.java                     # E.164 normalisation
    ├── CurrencyUtil.java                  # Format amounts per country
    ├── OtpUtil.java                       # 4-digit (trip start) and 6-digit (auth) OTP generation
    └── PaginationUtil.java               # Page/sort helpers
```

---

## pom.xml

```xml
<project>
    <parent>
        <groupId>com.twende</groupId>
        <artifactId>twende-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>common-lib</artifactId>
    <packaging>jar</packaging>
    <name>Twende Common Library</name>
    <description>Shared DTOs, events, enums, utils, and security filters</description>

    <!-- Plain JAR, NOT a Spring Boot executable -->
    <!-- Do NOT include spring-boot-maven-plugin -->

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
        </dependency>
        <dependency>
            <groupId>com.github.f4b6a3</groupId>
            <artifactId>ulid-creator</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

## Core Classes

### BaseEntity (ULID-based primary keys)

ULIDs are time-sortable, globally unique, and stored as standard `UUID` columns in PostgreSQL.
The Java type remains `UUID` -- ULIDs are binary-compatible with UUID. The custom `UlidGenerator`
produces monotonically increasing IDs for better B-tree index performance.

**Do NOT use `GenerationType.UUID`.** Use the custom `UlidGenerator` with `@GenericGenerator`.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(generator = "ulid")
    @GenericGenerator(name = "ulid", type = UlidGenerator.class)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 2)
    private String countryCode;  // present on all entities for multi-tenancy
}
```

Each consuming service must enable JPA auditing: `@EnableJpaAuditing` on its main class or
a config class.

### UlidGenerator

```java
import com.github.f4b6a3.ulid.UlidCreator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

public class UlidGenerator implements IdentifierGenerator {
    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        return UlidCreator.getMonotonicUlid().toUuid();
    }
}
```

---

### ApiResponse\<T\>

Every controller method across all services returns `ApiResponse<T>`.

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder().success(true).data(data).message(message).build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder().success(false).message(message).build();
    }
}
```

### PagedResponse\<T\>

Paginated wrapper for list endpoints. Wraps Spring `Page<T>` results.

---

### Global Exception Handler

Registered automatically via `CommonAutoConfiguration` when `common-lib` is on the classpath.

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(ResourceNotFoundException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleConflict(ConflictException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequest(BadRequestException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
        return ApiResponse.<Map<String, String>>builder()
            .success(false).message("Validation failed").data(errors).build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponse.error("An unexpected error occurred");
    }
}
```

### Exception Classes

```java
public class TwendeException extends RuntimeException {
    public TwendeException(String message) { super(message); }
    public TwendeException(String message, Throwable cause) { super(message, cause); }
}

public class ResourceNotFoundException extends TwendeException {
    public ResourceNotFoundException(String message) { super(message); }
}

public class UnauthorizedException extends TwendeException {
    public UnauthorizedException(String message) { super(message); }
}

public class ConflictException extends TwendeException {
    public ConflictException(String message) { super(message); }
}

public class BadRequestException extends TwendeException {
    public BadRequestException(String message) { super(message); }
}
```

---

### CommonAutoConfiguration

Registers the `GlobalExceptionHandler` as a bean automatically when `common-lib` is on the
classpath.

**Registration file:** `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.twende.common.config.CommonAutoConfiguration
```

```java
@Configuration
public class CommonAutoConfiguration {
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
```

---

## Enums

### RideStatus

```java
public enum RideStatus {
    REQUESTED,
    DRIVER_ASSIGNED,
    DRIVER_ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_DRIVER_FOUND
}
```

State machine transitions:
```
REQUESTED -> DRIVER_ASSIGNED -> DRIVER_ARRIVED -> IN_PROGRESS -> COMPLETED
     |              |
CANCELLED    CANCELLED (after assignment)
     |
NO_DRIVER_FOUND
```

### DriverStatus

```java
public enum DriverStatus {
    PENDING_APPROVAL,
    APPROVED,
    OFFLINE,
    ONLINE_AVAILABLE,
    ONLINE_ON_TRIP,
    SUSPENDED,
    REJECTED
}
```

### VehicleType

```java
public enum VehicleType {
    BAJAJ,         // Tanzania tuk-tuk
    BODA_BODA,     // motorcycle
    CAR_ECONOMY,
    CAR_COMFORT,
    TUKTUK         // used in Kenya/Uganda
}
```

### PaymentStatus

```java
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED
}
```

### PaymentMethod

```java
public enum PaymentMethod {
    MOBILE_MONEY,
    CASH,
    CARD
}
```

### SubscriptionPlan

```java
public enum SubscriptionPlan {
    DAILY,
    WEEKLY,
    MONTHLY
}
```

### SubscriptionStatus

```java
public enum SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED,
    PENDING_PAYMENT
}
```

### NotificationType

```java
public enum NotificationType {
    PUSH, SMS, IN_APP, EMAIL
}
```

### DocumentType

```java
public enum DocumentType {
    NATIONAL_ID,
    DRIVING_LICENSE,
    PSV_LICENSE,
    VEHICLE_INSPECTION,
    VEHICLE_INSURANCE,
    PROFILE_PHOTO,
    VEHICLE_PHOTO
}
```

### CountryCode

```java
public enum CountryCode {
    TZ, KE, UG
}
```

### UserRole

```java
public enum UserRole {
    RIDER,
    DRIVER,
    ADMIN,
    SUPPORT
}
```

### DriverOfferAction

```java
public enum DriverOfferAction {
    ACCEPT,
    REJECT,
    TIMEOUT  // 15-second window expired without action
}
```

### OfferStatus

For loyalty free ride offers.

```java
public enum OfferStatus {
    AVAILABLE,
    REDEEMED,
    EXPIRED
}
```

---

## Kafka Events

All inter-service communication uses Kafka. **Do NOT use Spring `ApplicationEvent` for
cross-service messaging.** `ApplicationEvent` is for intra-process only.

### KafkaEvent (base class)

All event classes extend this. Every event carries an ID, type, country code, timestamp,
and correlation ID for distributed tracing.

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class KafkaEvent {
    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private String countryCode;
    private Instant timestamp = Instant.now();
    private String correlationId;  // trace ID for distributed tracing
}
```

### Ride Events (`com.twende.common.event.ride`)

#### RideRequestedEvent

Published when a rider creates a new ride request.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class RideRequestedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID riderId;
    private VehicleType vehicleType;
    private Location pickupLocation;
    private Location dropoffLocation;
    private BigDecimal estimatedFare;
}
```

#### RideStatusUpdatedEvent

Published on any ride status transition.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class RideStatusUpdatedEvent extends KafkaEvent {
    private UUID rideId;
    private RideStatus previousStatus;
    private RideStatus newStatus;
}
```

#### RideCompletedEvent

Published when a ride reaches `COMPLETED` status.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class RideCompletedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID riderId;
    private UUID driverId;
    private BigDecimal finalFare;
    private Integer distanceMetres;
    private Integer durationSeconds;
    private Instant startedAt;
    private Instant completedAt;
    private boolean freeRide;
    private UUID freeRideOfferId;
}
```

#### RideFareBoostedEvent

Published when a rider boosts their fare during `REQUESTED` status.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class RideFareBoostedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID riderId;
    private BigDecimal previousFare;
    private BigDecimal newFare;
    private BigDecimal boostAmount;
}
```

### Driver Events (`com.twende.common.event.driver`)

#### DriverMatchedEvent

Published when a driver is assigned to a ride (won the accept race).

```java
@Data @EqualsAndHashCode(callSuper = true)
public class DriverMatchedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID driverId;
    private UUID riderId;
    private Integer estimatedArrivalSeconds;
}
```

#### DriverStatusUpdatedEvent

Published when a driver's status changes (online, offline, on-trip, etc.).

```java
@Data @EqualsAndHashCode(callSuper = true)
public class DriverStatusUpdatedEvent extends KafkaEvent {
    private UUID driverId;
    private DriverStatus previousStatus;
    private DriverStatus newStatus;
}
```

#### DriverRejectedRideEvent

Published when a driver explicitly rejects a ride offer.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class DriverRejectedRideEvent extends KafkaEvent {
    private UUID rideId;
    private UUID driverId;
    private int newRejectionCount;
}
```

#### DriverOfferNotificationEvent

Published by the matching engine to trigger a push notification to a driver about a new ride offer.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class DriverOfferNotificationEvent extends KafkaEvent {
    private UUID rideId;
    private UUID driverId;
    private double pickupDistanceKm;
    private String pickupAreaName;
    private double estimatedTripDistanceKm;
    private int estimatedTripMinutes;
    private BigDecimal totalFare;
    private BigDecimal boostAmount;
    private String currencyCode;
    private int offerWindowSeconds;
}
```

#### RideOfferAcceptedEvent

Published when a driver wins the accept race for a ride.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class RideOfferAcceptedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID driverId;
    private int estimatedArrivalSeconds;
}
```

### Payment Events (`com.twende.common.event.payment`)

#### PaymentInitiatedEvent

```java
@Data @EqualsAndHashCode(callSuper = true)
public class PaymentInitiatedEvent extends KafkaEvent {
    private UUID transactionId;
    private UUID userId;
    private BigDecimal amount;
    private String currencyCode;
    private String paymentMethod;
}
```

#### PaymentCompletedEvent

```java
@Data @EqualsAndHashCode(callSuper = true)
public class PaymentCompletedEvent extends KafkaEvent {
    private UUID transactionId;
    private UUID userId;
    private BigDecimal amount;
    private PaymentStatus status;
}
```

### Subscription Events (`com.twende.common.event.subscription`)

#### SubscriptionActivatedEvent

```java
@Data @EqualsAndHashCode(callSuper = true)
public class SubscriptionActivatedEvent extends KafkaEvent {
    private UUID subscriptionId;
    private UUID driverId;
    private SubscriptionPlan plan;
    private Instant expiresAt;
}
```

#### SubscriptionExpiredEvent

```java
@Data @EqualsAndHashCode(callSuper = true)
public class SubscriptionExpiredEvent extends KafkaEvent {
    private UUID subscriptionId;
    private UUID driverId;
}
```

### User Events (`com.twende.common.event.user`)

#### UserRegisteredEvent

```java
@Data @EqualsAndHashCode(callSuper = true)
public class UserRegisteredEvent extends KafkaEvent {
    private UUID userId;
    private String fullName;
    private String phoneNumber;
    private UserRole role;
}
```

### Notification Events (`com.twende.common.event.notification`)

#### SendNotificationEvent

Generic notification event consumed by the notification-service.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class SendNotificationEvent extends KafkaEvent {
    private UUID recipientUserId;
    private NotificationType type;
    private String titleKey;    // i18n key e.g. "notification.ride.accepted.title"
    private String bodyKey;
    private Map<String, String> templateParams;
    private Map<String, String> data;  // FCM data payload
}
```

### Loyalty Events (`com.twende.common.event.loyalty`)

#### FreeRideOfferEarnedEvent

Published when a rider earns a free ride offer by reaching loyalty thresholds.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class FreeRideOfferEarnedEvent extends KafkaEvent {
    private UUID offerId;
    private UUID riderId;
    private VehicleType vehicleType;
    private BigDecimal maxDistanceKm;
    private Instant expiresAt;
}
```

#### FreeRideCompletedEvent

Published when a free (loyalty) ride is completed. Triggers wallet credit to the driver.

```java
@Data @EqualsAndHashCode(callSuper = true)
public class FreeRideCompletedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID riderId;
    private UUID driverId;
    private UUID freeRideOfferId;
    private BigDecimal fareAmount;
    private String currencyCode;
}
```

---

## Utilities

### PhoneUtil -- E.164 Normalisation

All phone numbers must be normalised to E.164 format before storing or comparing.
`+255712345678`, not `0712345678` or `255712345678`.

```java
public final class PhoneUtil {

    private PhoneUtil() {}

    /**
     * Normalise a phone number to E.164 format.
     * @param phoneNumber raw input (may start with 0, country code without +, or already E.164)
     * @param countryCode ISO 3166-1 alpha-2 (e.g. "TZ", "KE", "UG")
     * @return E.164 formatted number (e.g. "+255712345678")
     * @throws BadRequestException if the number cannot be normalised
     */
    public static String normalise(String phoneNumber, String countryCode) {
        // Strip whitespace, dashes, parentheses
        // Map country code to phone prefix: TZ -> +255, KE -> +254, UG -> +256
        // If starts with "0", replace leading 0 with country prefix
        // If starts with country digits (e.g. "255"), prepend "+"
        // If already starts with "+", validate prefix matches country
        // Validate total length (TZ: 13 chars including +)
        ...
    }
}
```

### CurrencyUtil -- Format Amounts Per Country

```java
public final class CurrencyUtil {

    private CurrencyUtil() {}

    /**
     * Format a monetary amount for display.
     * Tanzania (TZS): no decimal places, e.g. "TSh 2,000"
     * Kenya (KES): 2 decimal places, e.g. "KSh 150.00"
     */
    public static String format(BigDecimal amount, String currencyCode) { ... }
}
```

### OtpUtil -- OTP Generation

Uses `SecureRandom` for cryptographic safety. Two methods for the two OTP use cases:

```java
public final class OtpUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OtpUtil() {}

    /**
     * Generate a 4-digit OTP for trip start verification.
     * Driver enters this code (shared verbally by the rider) to begin the trip.
     */
    public static String generate4Digit() {
        return String.format("%04d", SECURE_RANDOM.nextInt(10000));
    }

    /**
     * Generate a 6-digit OTP for phone authentication.
     * Sent via SMS to the user's phone number during login.
     */
    public static String generate6Digit() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1000000));
    }
}
```

### PaginationUtil -- Page/Sort Helpers

```java
public final class PaginationUtil {

    private PaginationUtil() {}

    /**
     * Build a PageRequest with sensible defaults.
     * @param page zero-based page number (default 0)
     * @param size page size (default 20, max 100)
     * @param sortBy field to sort by (default "createdAt")
     * @param direction sort direction (default DESC)
     */
    public static PageRequest buildPageRequest(int page, int size, String sortBy, Sort.Direction direction) {
        size = Math.min(size, 100);
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}
```

---

## Conventions

### Money -- NEVER use double or float

```java
// Correct
private BigDecimal amount;  // Java field
// DB column: NUMERIC(12,2)

// Wrong -- never do this
private double amount;
private float amount;
```

### Timestamps -- always UTC, always Instant

```java
private Instant createdAt;      // correct
private LocalDateTime time;     // wrong -- no timezone info
private Date date;              // wrong -- legacy
```

### All entities extend BaseEntity

Every JPA entity across every service extends `BaseEntity`. This gives automatic ULID-based
primary keys, audit timestamps, and country code for multi-tenancy.

### API responses always wrapped

Every REST endpoint returns `ResponseEntity<ApiResponse<T>>`. No raw objects.

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<RideDto>> getRide(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(rideService.getRide(id)));
}
```

### Validation on incoming requests

```java
@PostMapping
public ResponseEntity<ApiResponse<...>> create(@Valid @RequestBody CreateRideRequest req) { ... }
```

---

## Build Notes

- This is a plain JAR, not a Spring Boot application. Do NOT add `spring-boot-maven-plugin`.
- Consuming services declare this as a dependency:
  ```xml
  <dependency>
      <groupId>com.twende</groupId>
      <artifactId>common-lib</artifactId>
      <version>${project.version}</version>
  </dependency>
  ```
- The `GlobalExceptionHandler` is registered automatically via Spring Boot auto-configuration.
  No manual `@Import` needed in consuming services.
- Each consuming service must add `@EnableJpaAuditing` to activate `@CreatedDate` / `@LastModifiedDate`.
- The `ulid-creator` dependency (version managed by parent POM) is required for `UlidGenerator`.

---

## Charter, Cargo & Flat Fee Expansion (Phase 7-9)

### New Enums (Phase 7)

- `ServiceCategory`: `RIDE`, `CHARTER`, `CARGO` -- classifies the type of transport service
- `RevenueModel`: `SUBSCRIPTION`, `FLAT_FEE` -- how the driver pays Twende
- `BookingType`: `ON_DEMAND`, `SCHEDULED` -- immediate vs future pickup
- `QualityTier`: `STANDARD`, `LUXURY` -- quality level for charter vehicles
- `TripDirection`: `ONE_WAY`, `ROUND_TRIP` -- for charter bookings
- `WeightTier`: `LIGHT`, `MEDIUM`, `FULL` -- cargo weight classification. LIGHT = small items/few boxes (e.g. moving a TV + bags), MEDIUM = partial truck load (e.g. office furniture), FULL = full truck capacity (e.g. full house move)

### New VehicleType Values (Phase 7)

Add to existing `VehicleType` enum:
- `MINIBUS_STANDARD` -- standard charter minibus (14-25 passengers)
- `MINIBUS_LUXURY` -- luxury charter minibus (14-25 passengers)
- `BUS_STANDARD` -- standard charter bus (26-50 passengers)
- `BUS_LUXURY` -- luxury charter bus (26-50 passengers)
- `CARGO_TUKTUK` -- cargo tuk-tuk for small loads
- `TRUCK_LIGHT` -- light truck (up to 1.5 tons)
- `TRUCK_MEDIUM` -- medium truck (1.5-5 tons)
- `TRUCK_HEAVY` -- heavy truck (5+ tons)

### New Kafka Events (Phase 7)

- `BookingRequestedEvent` (`com.twende.common.event.ride`) -- published when a charter or cargo booking is created. Includes `serviceCategory`, `bookingType`, `scheduledPickupAt`, `qualityTier`, `weightTier` (cargo), `driverProvidesLoading` (cargo)
- `BookingCompletedEvent` (`com.twende.common.event.ride`) -- published when a charter or cargo trip completes. Includes `serviceCategory`, final fare, cargo/charter-specific fields
- `FlatFeeDeductedEvent` (`com.twende.common.event.payment`) -- published when Twende's flat fee cut is deducted from a driver's wallet. Includes `driverId`, `rideId`, `fareAmount`, `feePercentage`, `feeAmount`

---

## Implementation Steps

- [ ] 1. BaseEntity + UlidGenerator (ULID-based UUID primary keys, audit timestamps, countryCode)
- [ ] 2. ApiResponse\<T\> + PagedResponse\<T\> (standard response wrappers)
- [ ] 3. All enums: RideStatus, DriverStatus, VehicleType, PaymentStatus, PaymentMethod, SubscriptionPlan, SubscriptionStatus, NotificationType, DocumentType, CountryCode, UserRole, DriverOfferAction, OfferStatus
- [ ] 4. Exception hierarchy: TwendeException (base), ResourceNotFoundException, UnauthorizedException, ConflictException, BadRequestException
- [ ] 5. GlobalExceptionHandler (@RestControllerAdvice) + CommonAutoConfiguration (Spring auto-config registration)
- [ ] 6. KafkaEvent base class + all event POJOs (RideRequestedEvent, RideStatusUpdatedEvent, RideCompletedEvent, RideFareBoostedEvent, DriverMatchedEvent, DriverStatusUpdatedEvent, DriverRejectedRideEvent, DriverOfferNotificationEvent, RideOfferAcceptedEvent, PaymentInitiatedEvent, PaymentCompletedEvent, SubscriptionActivatedEvent, SubscriptionExpiredEvent, UserRegisteredEvent, SendNotificationEvent, FreeRideOfferEarnedEvent, FreeRideCompletedEvent)
- [ ] 7. Utilities: PhoneUtil (E.164 normalization), CurrencyUtil (format per country), OtpUtil (4-digit + 6-digit via SecureRandom), PaginationUtil
- [ ] 8. Unit tests for all utilities and enums (>=80% coverage)
- [ ] 9. Verify build: `./mvnw -pl common-lib clean install`
