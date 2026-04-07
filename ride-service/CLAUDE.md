# CLAUDE.md -- Ride Service

> Core orchestrator of the ride lifecycle. Owns the ride entity from request to completion
> or cancellation. Coordinates with matching-engine, pricing-engine, payment-service,
> loyalty-service, location-service, and notification-service via Kafka and synchronous REST.

---

## 1. Service Identity

| Property | Value |
|---|---|
| Artifact | `ride-service` |
| Port | `8085` |
| Database | `twende_rides` (PostgreSQL 16) |
| Base package | `com.twende.ride` |
| Parent POM | `twende-parent` |

---

## 2. Ride Lifecycle -- State Machine

```
REQUESTED --> DRIVER_ASSIGNED --> DRIVER_ARRIVED --> IN_PROGRESS --> COMPLETED
     |               |
     +-- CANCELLED   +-- CANCELLED (driver or rider cancels after assignment)
     |
     +-- NO_DRIVER_FOUND (matching timeout, 3 min)
```

**Transition rules:**
- `REQUESTED -> DRIVER_ASSIGNED`: only via internal endpoint from matching-engine
- `REQUESTED -> CANCELLED`: rider cancels before driver assigned (no fee)
- `REQUESTED -> NO_DRIVER_FOUND`: matching timeout scheduler (3 min with no accept)
- `DRIVER_ASSIGNED -> DRIVER_ARRIVED`: driver marks arrival
- `DRIVER_ASSIGNED -> CANCELLED`: rider or driver cancels (cancellation fee if rider cancels after arrived_at)
- `DRIVER_ARRIVED -> IN_PROGRESS`: driver submits valid OTP (mandatory, no bypass)
- `IN_PROGRESS -> COMPLETED`: driver ends trip, final fare calculated

**Invalid transitions are rejected with 400 Bad Request.** Every transition is logged to
`ride_status_events` for audit.

---

## 3. Ride Entity -- All Fields

```java
@Entity
@Table(name = "rides")
@Getter @Setter @NoArgsConstructor
public class Ride extends BaseEntity {
    // Identity
    private UUID riderId;
    private UUID driverId;                  // null until matched
    private RideStatus status;
    private VehicleType vehicleType;
    private UUID cityId;                    // resolved from pickup location at creation

    // Pickup
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private String pickupAddress;

    // Dropoff
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private String dropoffAddress;

    // Fare
    private BigDecimal estimatedFare;       // base + boost, updated on each boost
    private BigDecimal fareBoostAmount;     // cumulative boost only, default 0
    private BigDecimal finalFare;           // set on completion
    private String currencyCode;

    // Loyalty / Free ride
    private boolean freeRide;               // true if loyalty offer applied
    private UUID freeRideOfferId;           // FK to loyalty-service free_ride_offers, nullable

    // Driver rejection tracking
    private int driverRejectionCount;       // incremented on each explicit reject

    // Trip start OTP
    private String tripStartOtpHash;        // bcrypt (cost 4), null after use
    private Instant tripStartOtpExpiresAt;  // 10 min from generation
    private int tripStartOtpAttempts;       // max 3 before regeneration

    // Trip metrics
    private Integer distanceMetres;
    private Integer durationSeconds;

    // Timestamps
    private Instant requestedAt;
    private Instant assignedAt;
    private Instant arrivedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;

    // Cancellation
    private String cancelReason;
    private String cancelledBy;             // RIDER | DRIVER | SYSTEM

    // Matching
    private Instant matchingTimeoutAt;      // requestedAt + 3 min
}
```

**Money fields use `BigDecimal` only. Never `double` or `float`.**
Database columns: `NUMERIC(12,2)`.

---

## 4. Database Schema

### Table: rides

```sql
CREATE TABLE rides (
    id                    UUID          PRIMARY KEY,
    country_code          CHAR(2)       NOT NULL,
    rider_id              UUID          NOT NULL,
    driver_id             UUID,
    vehicle_type          VARCHAR(30)   NOT NULL,
    status                VARCHAR(30)   NOT NULL DEFAULT 'REQUESTED',
    city_id               UUID,

    -- Pickup
    pickup_lat            NUMERIC(10,7) NOT NULL,
    pickup_lng            NUMERIC(10,7) NOT NULL,
    pickup_address        VARCHAR(300)  NOT NULL,

    -- Dropoff
    dropoff_lat           NUMERIC(10,7) NOT NULL,
    dropoff_lng           NUMERIC(10,7) NOT NULL,
    dropoff_address       VARCHAR(300)  NOT NULL,

    -- Fare
    estimated_fare        NUMERIC(12,2) NOT NULL,
    fare_boost_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    final_fare            NUMERIC(12,2),
    currency_code         VARCHAR(3)    NOT NULL,

    -- Loyalty
    free_ride             BOOLEAN       NOT NULL DEFAULT FALSE,
    free_ride_offer_id    UUID,

    -- Rejection tracking
    driver_rejection_count INT          NOT NULL DEFAULT 0,

    -- Trip start OTP
    trip_start_otp_hash       VARCHAR(100),
    trip_start_otp_expires_at TIMESTAMPTZ,
    trip_start_otp_attempts   INT        NOT NULL DEFAULT 0,

    -- Metrics
    distance_metres       INT,
    duration_seconds      INT,

    -- Timestamps
    requested_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    assigned_at           TIMESTAMPTZ,
    arrived_at            TIMESTAMPTZ,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    cancel_reason         TEXT,
    cancelled_by          VARCHAR(10),
    matching_timeout_at   TIMESTAMPTZ,

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_rides_rider    ON rides(rider_id, status);
CREATE INDEX idx_rides_driver   ON rides(driver_id, status);
CREATE INDEX idx_rides_status   ON rides(status);
CREATE INDEX idx_rides_country  ON rides(country_code, status);
CREATE INDEX idx_rides_requested ON rides(requested_at);
```

### Table: ride_status_events

```sql
CREATE TABLE ride_status_events (
    id          UUID        PRIMARY KEY,
    ride_id     UUID        NOT NULL REFERENCES rides(id),
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    actor_id    UUID,
    actor_role  VARCHAR(10),
    metadata    JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Table: ride_driver_rejections

```sql
CREATE TABLE ride_driver_rejections (
    id          UUID        PRIMARY KEY,
    ride_id     UUID        NOT NULL REFERENCES rides(id),
    driver_id   UUID        NOT NULL,
    rejected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rejections_ride ON ride_driver_rejections(ride_id);
```

---

## 5. API Endpoints

### Rider Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/rides` | Request a new ride |
| `GET` | `/api/v1/rides/{id}` | Get ride status (includes `driverRejectionCount`, `fareBoostAmount`) |
| `PUT` | `/api/v1/rides/{id}/boost` | Increase fare offer (fare boost) |
| `DELETE` | `/api/v1/rides/{id}` | Cancel a ride |
| `GET` | `/api/v1/rides/history` | Paginated ride history |
| `POST` | `/api/v1/rides/{id}/otp/resend` | Resend trip start OTP to rider |

### Driver Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/rides/current` | Driver's current active ride |
| `POST` | `/api/v1/rides/{id}/accept` | Accept ride offer (delegates to matching-service) |
| `POST` | `/api/v1/rides/{id}/reject` | Reject ride offer (delegates to matching-service) |
| `PUT` | `/api/v1/rides/{id}/arrived` | Mark as arrived (triggers OTP generation) |
| `POST` | `/api/v1/rides/{id}/start` | Start trip by submitting OTP |
| `PUT` | `/api/v1/rides/{id}/complete` | Complete trip |

### Internal Endpoints

| Method | Path | Description |
|---|---|---|
| `PUT` | `/internal/rides/{id}/offer-accepted` | Called by matching-engine on driver accept |
| `PUT` | `/internal/rides/{id}/driver-rejected` | Increment rejection count |
| `GET` | `/internal/rides/{id}` | Full ride detail (used by payment, compliance) |

### Request/Response Examples

**Create ride:**
```json
POST /api/v1/rides
Authorization: Bearer {riderToken}
{
  "vehicleType": "BAJAJ",
  "pickupLocation": {
    "latitude": -6.7728,
    "longitude": 39.2310,
    "address": "Kariakoo Market, Dar es Salaam"
  },
  "dropoffLocation": {
    "latitude": -6.8160,
    "longitude": 39.2803,
    "address": "Mlimani City Mall"
  }
}

// Response:
{
  "success": true,
  "data": {
    "rideId": "uuid",
    "status": "REQUESTED",
    "estimatedFare": {
      "amount": 3500,
      "currency": "TZS",
      "display": "TSh 3,500"
    },
    "freeRide": false,
    "estimatedWaitSeconds": 120
  }
}
```

**Boost fare:**
```json
PUT /api/v1/rides/{id}/boost
Authorization: Bearer {riderToken}
{
  "boostAmount": 1000
}

// Response:
{
  "success": true,
  "data": {
    "rideId": "uuid",
    "previousFare": 3500,
    "newFare": 4500,
    "boostAmount": 1000,
    "currency": "TZS"
  }
}
```

**Start trip with OTP:**
```json
POST /api/v1/rides/{id}/start
Authorization: Bearer {driverToken}
{
  "otp": "4821"
}

// Success:
{
  "success": true,
  "data": { "status": "IN_PROGRESS", "startedAt": "2025-06-01T08:30:00Z" }
}

// Wrong OTP:
{
  "success": false,
  "message": "Incorrect code. 2 attempt(s) remaining."
}

// Max attempts exceeded:
{
  "success": false,
  "message": "Too many incorrect attempts. A new code has been sent to the rider."
}
```

---

## 6. Ride Request Flow (Detailed)

```
1. Rider POSTs /api/v1/rides
2. ride-service:
   a. Validates request fields
   b. Calls location-service (REST): geofence check -- is pickup in service area?
      - If pickup is in a RESTRICTED zone: reject with error
   c. Calls pricing-service (REST): GET fare estimate
   d. Calls loyalty-service (REST): find applicable free ride offer
      - If offer found: set freeRide=true, freeRideOfferId, fare=0 for rider
      - Redeem the offer atomically
   e. Creates ride record with status=REQUESTED
   f. Sets matchingTimeoutAt = now() + 3 minutes
   g. Publishes RideRequestedEvent to Kafka
   h. Returns rideId + estimated fare to rider

3. matching-engine consumes RideRequestedEvent
   - Finds eligible nearby drivers (broadcast-and-accept model)
   - All nearby drivers receive simultaneous push notification
   - First driver to ACCEPT wins (Redis atomic SETNX)

4. matching-engine calls PUT /internal/rides/{id}/offer-accepted
   a. ride-service updates: driverId, status=DRIVER_ASSIGNED, assignedAt
   b. Publishes RideStatusUpdatedEvent
   c. notification-service notifies rider and driver

5. Driver arrives -> PUT /api/v1/rides/{id}/arrived
   - Status -> DRIVER_ARRIVED
   - OTP generated and sent to rider (see section 9)

6. Driver starts trip -> POST /api/v1/rides/{id}/start { otp: "4821" }
   - OTP validated (see section 9)
   - Status -> IN_PROGRESS, startedAt recorded

7. Driver ends trip -> PUT /api/v1/rides/{id}/complete
   a. Calls pricing-service (REST): calculate final fare with actual distance + duration
   b. Updates ride with finalFare, distanceMetres, durationSeconds, completedAt
   c. Publishes RideCompletedEvent to Kafka
   d. payment-service consumes event:
      - If freeRide: credits driver wallet with full calculated fare (Twende absorbs cost)
      - If cash: no digital processing needed

8. Matching timeout (scheduler, every 30s):
   - Finds rides where status=REQUESTED AND matchingTimeoutAt < now()
   - Sets status=NO_DRIVER_FOUND, publishes event, notifies rider
```

---

## 7. Fare Boost

Allows a rider to increase their fare offer to attract drivers faster. Boost goes 100%
to the driver -- Twende takes no cut.

### Validation Rules

- Ride must be in `REQUESTED` status (not after `DRIVER_ASSIGNED`)
- `boostAmount` must be > 0 (upward only, can never reduce fare)
- `newFare` must not exceed `baseFare * surgeMultiplierCap` from country config vehicle type
- Rider must own the ride (riderId matches JWT subject)
- Multiple boosts allowed, each adds to cumulative `fareBoostAmount`

### Implementation

```java
public void boostFare(UUID rideId, UUID riderId, BigDecimal boostAmount) {
    Ride ride = findByIdAndRider(rideId, riderId);

    if (ride.getStatus() != RideStatus.REQUESTED)
        throw new BadRequestException("Can only boost fare before a driver is assigned");
    if (boostAmount.compareTo(BigDecimal.ZERO) <= 0)
        throw new BadRequestException("Boost amount must be positive");

    // Fetch max fare cap from country config
    VehicleTypeConfig vtc = countryConfigClient.getVehicleTypeConfig(
        ride.getCountryCode(), ride.getVehicleType());
    BigDecimal maxFare = vtc.getBaseFare().multiply(vtc.getSurgeMultiplierCap());
    BigDecimal newFare = ride.getEstimatedFare().add(boostAmount);

    if (newFare.compareTo(maxFare) > 0)
        throw new BadRequestException("Fare cannot exceed maximum of " + maxFare);

    ride.setFareBoostAmount(ride.getFareBoostAmount().add(boostAmount));
    ride.setEstimatedFare(newFare);
    rideRepository.save(ride);

    // Publish event -> matching-engine re-broadcasts to un-offered drivers
    kafkaTemplate.send("twende.rides.fare-boosted", RideFareBoostedEvent.builder()
        .rideId(ride.getId())
        .riderId(riderId)
        .previousFare(newFare.subtract(boostAmount))
        .newFare(newFare)
        .boostAmount(boostAmount)
        .countryCode(ride.getCountryCode())
        .build());
}
```

### Post-Boost Behavior

- matching-engine consumes `RideFareBoostedEvent` and re-broadcasts the offer to all
  eligible drivers who have NOT yet been offered or rejected
- Drivers who already rejected this ride are permanently excluded (check
  `driver_rejected:{rideId}` Redis set before re-broadcasting)
- The updated fare is shown in the driver's offer card

---

## 8. Driver Rejection Counter

Tracks how many drivers have explicitly rejected a ride offer. Rider sees the count
in real time to inform their decision to boost the fare.

### What Counts as a Rejection

- Driver sees the offer card and explicitly presses "Reject"
- Does NOT include: driver never saw the offer, driver's app timed out (15s window),
  driver went offline before the offer arrived

### Kafka Consumer

```java
@KafkaListener(topics = "twende.drivers.rejected-ride", groupId = "ride-service")
public void handleDriverRejection(DriverRejectedRideEvent event) {
    Ride ride = rideRepository.findById(event.getRideId()).orElseThrow();
    ride.setDriverRejectionCount(ride.getDriverRejectionCount() + 1);

    // Audit log
    rideDriverRejectionRepository.save(
        new RideDriverRejection(ride.getId(), event.getDriverId()));

    rideRepository.save(ride);

    // Push live update to rider via notification-service
    kafkaTemplate.send("twende.notifications.send", SendNotificationEvent.builder()
        .recipientUserId(ride.getRiderId())
        .type(NotificationType.IN_APP)
        .data(Map.of(
            "type", "REJECTION_COUNT_UPDATE",
            "rideId", ride.getId().toString(),
            "count", String.valueOf(ride.getDriverRejectionCount())
        ))
        .build());

    // At 3 rejections: nudge rider to boost fare
    if (ride.getDriverRejectionCount() == 3) {
        kafkaTemplate.send("twende.notifications.send", SendNotificationEvent.builder()
            .recipientUserId(ride.getRiderId())
            .type(NotificationType.PUSH)
            .titleKey("notification.ride.boost-nudge.title")
            .bodyKey("notification.ride.boost-nudge.body")
            .data(Map.of(
                "type", "FARE_BOOST_NUDGE",
                "rideId", ride.getId().toString()
            ))
            .build());
    }
}
```

### Real-Time Update

The rider's app listens for `REJECTION_COUNT_UPDATE` data messages via WebSocket/push
and updates the counter display without polling. If `count >= 3`, the app surfaces the
fare boost prompt automatically.

---

## 9. Trip Start OTP

Prevents fraudulent trip starts. The driver must enter a 4-digit code that only the
physically present rider knows.

### OTP Generation (on DRIVER_ARRIVED)

```java
private void generateAndSendTripOtp(Ride ride) {
    String otp = String.format("%04d", new SecureRandom().nextInt(10000));
    ride.setTripStartOtpHash(passwordEncoder.encode(otp));  // bcrypt, cost 4
    ride.setTripStartOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    ride.setTripStartOtpAttempts(0);
    rideRepository.save(ride);

    // Notify rider via Kafka -> notification-service
    kafkaTemplate.send("twende.notifications.send", SendNotificationEvent.builder()
        .recipientUserId(ride.getRiderId())
        .type(NotificationType.PUSH)
        .titleKey("notification.trip.otp.title")
        .bodyKey("notification.trip.otp.body")
        .templateParams(Map.of("otp", otp, "driverName", getDriverName(ride.getDriverId())))
        .data(Map.of("otp", otp, "rideId", ride.getId().toString(), "type", "TRIP_OTP"))
        .build());
}
```

The OTP is sent in the FCM `data` payload (not the notification body) so the app can
display it in a custom styled view. The notification body says "Your driver has arrived --
share code **4821** to begin."

### OTP Verification (Start Trip)

```java
public void startTripWithOtp(UUID rideId, UUID driverId, String otp) {
    Ride ride = findByIdAndDriver(rideId, driverId);

    if (ride.getStatus() != RideStatus.DRIVER_ARRIVED)
        throw new BadRequestException("Driver must be marked as arrived first");

    // Check expiry
    if (ride.getTripStartOtpExpiresAt().isBefore(Instant.now())) {
        regenerateAndSendOtp(ride);
        throw new BadRequestException("Code expired. A new code has been sent to the rider.");
    }

    // Check hash match
    if (!passwordEncoder.matches(otp, ride.getTripStartOtpHash())) {
        ride.setTripStartOtpAttempts(ride.getTripStartOtpAttempts() + 1);

        if (ride.getTripStartOtpAttempts() >= 3) {
            regenerateAndSendOtp(ride);
            throw new BadRequestException(
                "Too many incorrect attempts. A new code has been sent to the rider.");
        }

        int remaining = 3 - ride.getTripStartOtpAttempts();
        rideRepository.save(ride);
        throw new BadRequestException(
            "Incorrect code. " + remaining + " attempt(s) remaining.");
    }

    // Valid OTP -- start the trip
    ride.setStatus(RideStatus.IN_PROGRESS);
    ride.setStartedAt(Instant.now());
    ride.setTripStartOtpHash(null);  // single-use -- null out immediately
    rideRepository.save(ride);
    publishStatusUpdate(ride);
}
```

### OTP Rules (Non-Negotiable)

1. **4 digits only** -- easy to communicate verbally in noisy market environments
2. **BCrypt hashed (cost 4)** -- never stored in plaintext
3. **Single-use** -- hash is nulled immediately after successful verification
4. **10-minute expiry** -- gives rider and driver time to meet
5. **Max 3 attempts** -- after 3 wrong entries, OTP is regenerated and re-sent
6. **Mandatory** -- no way to bypass OTP and move to IN_PROGRESS. No exceptions.
7. **Resend option** -- rider can request a new OTP via `POST /api/v1/rides/{id}/otp/resend`

---

## 10. Free Ride Integration (Loyalty)

The ride-service integrates with loyalty-service to support free rides earned through
the rider loyalty programme.

### On Ride Creation

```java
// After fare estimate, before saving ride
FreeRideOffer offer = loyaltyClient.findApplicableOffer(
    riderId, countryCode, vehicleType, estimatedDistanceKm);

if (offer != null) {
    ride.setFreeRide(true);
    ride.setFreeRideOfferId(offer.getId());
    ride.setEstimatedFare(BigDecimal.ZERO);  // rider pays nothing
    loyaltyClient.redeemOffer(offer.getId(), ride.getId());
}
```

### On Ride Completion

The `RideCompletedEvent` includes `freeRide` and `freeRideOfferId` fields. The
payment-service detects `ride.isFreeRide()` and credits the driver wallet with the
full calculated fare. Twende absorbs the cost -- driver is never penalised.

### Rules

- Free ride offers are vehicle-type-specific (Bajaj offer only redeemable on Bajaj ride)
- Free ride offers are distance-capped (offer specifies max km)
- Completed free rides do NOT count toward earning the next offer

---

## 11. Kafka Topics

### Published

| Topic | Event | Trigger |
|---|---|---|
| `twende.rides.requested` | `RideRequestedEvent` | New ride created |
| `twende.rides.status-updated` | `RideStatusUpdatedEvent` | Any status change |
| `twende.rides.completed` | `RideCompletedEvent` | Ride reaches COMPLETED |
| `twende.rides.cancelled` | `RideCancelledEvent` | Ride cancelled |
| `twende.rides.fare-boosted` | `RideFareBoostedEvent` | Rider boosts fare |

### Consumed

| Topic | Event | Action |
|---|---|---|
| `twende.rides.offer-accepted` | `RideOfferAcceptedEvent` | Assign driver to ride (also via internal REST) |
| `twende.drivers.rejected-ride` | `DriverRejectedRideEvent` | Increment rejection count, push update to rider |

---

## 12. Inter-Service Calls (RestClient)

| Target Service | Call | When |
|---|---|---|
| `pricing-service` | `GET /internal/pricing/estimate` | Ride creation -- get fare estimate |
| `pricing-service` | `POST /internal/pricing/calculate` | Ride completion -- calculate final fare |
| `location-service` | `GET /internal/locations/geofence/check` | Ride creation -- validate pickup is in service area, not in RESTRICTED zone |
| `loyalty-service` | `GET /internal/loyalty/offers/applicable` | Ride creation -- check for free ride offer |
| `loyalty-service` | `POST /internal/loyalty/offers/{id}/redeem` | Ride creation -- redeem free ride offer |
| `country-config-service` | `GET /internal/config/vehicle-types/{country}/{type}` | Fare boost -- get max fare cap |

All inter-service calls use Spring `RestClient`. No Feign. No WebClient.

---

## 13. Scheduled Jobs

```java
// Matching timeout checker -- runs every 30 seconds
@Scheduled(fixedDelay = 30_000)
public void checkMatchingTimeouts() {
    List<Ride> timedOut = rideRepository
        .findByStatusAndMatchingTimeoutAtBefore(RideStatus.REQUESTED, Instant.now());
    timedOut.forEach(ride -> {
        ride.setStatus(RideStatus.NO_DRIVER_FOUND);
        ride.setCancelledBy("SYSTEM");
        ride.setCancelReason("No driver found within matching window");
        ride.setCancelledAt(Instant.now());
        rideRepository.save(ride);
        logStatusEvent(ride, RideStatus.REQUESTED, RideStatus.NO_DRIVER_FOUND, null, "SYSTEM");
        publishStatusUpdate(ride);
    });
}
```

---

## 14. Cancellation Rules

| Scenario | Fee? | Details |
|---|---|---|
| Rider cancels before DRIVER_ASSIGNED | No | Free cancellation |
| Rider cancels after DRIVER_ASSIGNED but before DRIVER_ARRIVED | No | Grace period |
| Rider cancels after DRIVER_ARRIVED | Yes | Cancellation fee from `VehicleTypeConfig.cancellationFee` |
| Driver cancels after assignment | No fee to rider | Driver flagged; frequent cancellations reported to driver-service |
| System cancels (NO_DRIVER_FOUND) | No | Automatic timeout |

---

## 15. Idempotency

- `PUT /internal/rides/{id}/offer-accepted` is idempotent. If called twice for the same
  driver (duplicate Kafka delivery), it is a no-op if the ride is already `DRIVER_ASSIGNED`
  to that driver.
- `PUT /internal/rides/{id}/driver-rejected` is idempotent. Duplicate rejection events
  for the same driver + ride combination are detected via the `ride_driver_rejections`
  table (check before inserting).

---

## 16. Configuration

```yaml
server:
  port: 8085

spring:
  application:
    name: ride-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_rides
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    consumer:
      group-id: ride-service

twende:
  services:
    pricing-url: ${PRICING_SERVICE_URL:http://localhost:8083}
    location-url: ${LOCATION_SERVICE_URL:http://localhost:8084}
    loyalty-url: ${LOYALTY_SERVICE_URL:http://localhost:8088}
    country-config-url: ${COUNTRY_CONFIG_SERVICE_URL:http://localhost:8082}
```

---

## 17. Testing Strategy

### Unit Tests

- `RideService` state machine transitions -- test every valid and invalid transition
- `boostFare()` -- validation (wrong status, negative amount, exceeds cap, not ride owner)
- `startTripWithOtp()` -- correct OTP, wrong OTP, expired OTP, max attempts, regeneration
- Rejection counter increment and nudge at count == 3
- Free ride offer detection and redemption

### Integration Tests (Testcontainers)

```java
@SpringBootTest
@Testcontainers
class RideFlowIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    // Test: full ride lifecycle from creation to completion
    // Test: fare boost -> re-broadcast -> accept -> OTP -> complete
    // Test: 3 rejections -> nudge notification published
    // Test: free ride creation and completion with wallet credit
    // Test: matching timeout -> NO_DRIVER_FOUND
    // Test: cancellation fee logic
}
```

### Test Naming

```java
@Test
void givenRideInRequestedStatus_whenRiderBoostsFare_thenFareUpdatedAndEventPublished() {}

@Test
void givenRideInRequestedStatus_whenRiderBoostsBeyondCap_thenBadRequestThrown() {}

@Test
void givenDriverArrivedStatus_whenDriverEntersCorrectOtp_thenRideMovesToInProgress() {}

@Test
void givenThreeWrongOtpAttempts_whenDriverEntersFourthAttempt_thenNewOtpGeneratedAndSent() {}

@Test
void givenExpiredOtp_whenDriverSubmitsOtp_thenNewOtpGeneratedAndBadRequestThrown() {}

@Test
void givenThreeDriverRejections_whenFourthRejectionArrives_thenCountIncrementedAndNudgeSent() {}

@Test
void givenApplicableFreeRideOffer_whenRiderCreatesRide_thenRideMarkedFreeAndOfferRedeemed() {}
```

---

## 18. Business Rules (Non-Negotiable)

1. **OTP is mandatory for trip start.** There is no endpoint or flag to bypass OTP
   verification. IN_PROGRESS requires a valid OTP. No exceptions.

2. **Fare can only go up.** A rider can boost the fare but cannot reduce it. Once boosted,
   base fare + boost is the floor.

3. **Driver rejection is permanent for that ride.** A driver who rejected a ride must never
   receive another offer for the same ride, even if the fare is boosted. This is enforced
   by matching-engine checking the Redis `driver_rejected:{rideId}` set.

4. **OTP is single-use.** Null out `tripStartOtpHash` immediately after successful
   verification. Never re-use the same OTP.

5. **Money arithmetic uses BigDecimal only.** Never `double`, never `float`.

6. **Riders pay cash only.** No digital payment processing on the rider side. Selcom is
   only for driver subscriptions and wallet payouts.

7. **Free rides are Twende's cost.** When a loyalty free ride completes, payment-service
   credits the driver wallet with the full calculated fare. The driver is never penalised.

8. **Free ride offers are vehicle-type-specific and distance-capped.** An offer earned on
   Bajaj rides can only be redeemed on a Bajaj ride, and only if the trip distance is
   within the offer's max distance.

9. **Free rides do not count toward the next offer.** A completed free ride does not
   increment the rider's loyalty progress.

10. **Rides cannot start in RESTRICTED zones.** If the pickup point falls inside a zone
    with type RESTRICTED, reject the ride request at creation time.

11. **Every status transition is logged.** An entry in `ride_status_events` is created for
    every transition, with actor ID, role, and timestamp.

12. **Boost amount is 100% for the driver.** Twende takes no cut from the boost.
    This is part of the subscription-based business model.
