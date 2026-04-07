# CLAUDE.md -- Notification Service

> Twende Platform notification-service. Read this file fully before writing any code.

---

## 1. Overview

Purely event-driven notification delivery service. Consumes Kafka events and dispatches
notifications via push (FCM), SMS (Africa's Talking), in-app (DB + WebSocket), and email
(SendGrid). Has no outbound REST API called by other services -- all triggering happens
via Kafka. Other modules publish events; this module listens and delivers.

**Port:** 8091
**Database:** `twende_notifications`
**Base package:** `com.twende.notification`
**Shared library:** `com.twende.common` (common-lib dependency)

This is a standalone Spring Boot microservice in the Twende monorepo. It does NOT use Eureka,
Feign, or Spring Cloud. Inter-service communication uses Spring `RestClient` with direct URLs
resolved from configuration. Authentication context arrives via gateway-injected headers.

---

## 2. Package Structure

```
com.twende.notification
├── NotificationServiceApplication.java
├── config/
│   ├── RedisConfig.java
│   ├── KafkaConfig.java
│   ├── JpaConfig.java                # @EnableJpaAuditing
│   ├── AsyncConfig.java              # @EnableAsync thread pool
│   ├── FirebaseConfig.java           # FirebaseApp initialization
│   └── OpenApiConfig.java
├── entity/
│   ├── NotificationTemplate.java     # extends BaseEntity
│   ├── FcmToken.java                 # extends BaseEntity
│   └── NotificationLog.java          # extends BaseEntity
├── repository/
│   ├── NotificationTemplateRepository.java
│   ├── FcmTokenRepository.java
│   └── NotificationLogRepository.java
├── service/
│   ├── NotificationService.java      # Delegates to providers based on country config
│   └── TemplateResolver.java         # Resolves templateKey + locale with fallback
├── provider/
│   ├── SmsProvider.java              # Interface
│   ├── PushProvider.java             # Interface
│   ├── sms/
│   │   ├── AfricasTalkingSmsProvider.java   # RestClient, NO SDK
│   │   └── TwilioSmsProvider.java           # Stub for Kenya
│   └── push/
│       ├── FcmPushProvider.java             # Firebase Admin SDK
│       └── OneSignalPushProvider.java       # Stub
├── client/
│   └── CountryConfigClient.java      # RestClient to country-config-service
├── kafka/
│   ├── handler/
│   │   ├── RideStatusNotificationHandler.java
│   │   ├── RideCompletedNotificationHandler.java
│   │   ├── DriverApprovalNotificationHandler.java
│   │   ├── DriverOfferNotificationHandler.java
│   │   ├── PaymentNotificationHandler.java
│   │   ├── SubscriptionNotificationHandler.java
│   │   ├── LoyaltyNotificationHandler.java
│   │   └── DirectNotificationHandler.java
│   └── NotificationEventConsumer.java    # Routes Kafka messages to handlers
├── controller/
│   └── FcmTokenController.java       # POST /api/v1/notifications/fcm-token (register/update)
├── dto/
│   ├── request/
│   │   └── RegisterFcmTokenRequest.java
│   └── response/
│       └── NotificationDto.java
└── mapper/
    └── NotificationMapper.java       # MapStruct entity <-> DTO
```

---

## 3. Database Schema

Database: `twende_notifications` (isolated per-service database).
Schema managed by Flyway. Migrations in `src/main/resources/db/migration/`.

```sql
-- V1__create_notifications_schema.sql

CREATE TABLE notification_templates (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key VARCHAR(100) NOT NULL,          -- "ride.assigned.rider"
    locale       VARCHAR(10)  NOT NULL,           -- "sw-TZ", "en"
    channel      VARCHAR(10)  NOT NULL,           -- PUSH | SMS | EMAIL
    subject      VARCHAR(200),                    -- for email only
    body         TEXT         NOT NULL,           -- may contain {placeholders}
    country_code CHAR(2)      NOT NULL DEFAULT 'TZ',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(template_key, locale, channel)
);

CREATE TABLE fcm_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    token        TEXT        NOT NULL,
    platform     VARCHAR(10) NOT NULL,  -- ANDROID | IOS
    is_active    BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, token)
);

CREATE TABLE notification_log (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    country_code CHAR(2)     NOT NULL,
    channel      VARCHAR(10) NOT NULL,  -- PUSH | SMS | EMAIL | IN_APP
    template_key VARCHAR(100),
    title        VARCHAR(200),
    body         TEXT,
    status       VARCHAR(20) NOT NULL,  -- SENT | FAILED | SKIPPED
    provider     VARCHAR(30),           -- africastalking | fcm | twilio | sendgrid
    provider_ref VARCHAR(200),
    error        TEXT,
    sent_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fcm_user ON fcm_tokens(user_id) WHERE is_active = true;
CREATE INDEX idx_notif_log_user ON notification_log(user_id, sent_at DESC);
```

### Seed Templates

```sql
-- V2__seed_notification_templates.sql

INSERT INTO notification_templates (template_key, locale, channel, subject, body) VALUES
  -- Ride assigned to rider
  ('ride.assigned.rider', 'sw-TZ', 'PUSH', null,
   'Dereva {driverName} yuko njiani. Anakuja kwa {eta} dakika.'),
  ('ride.assigned.rider', 'en', 'PUSH', null,
   '{driverName} is on the way. Arriving in {eta} minutes.'),

  -- Ride completed
  ('ride.completed.rider', 'sw-TZ', 'PUSH', null,
   'Safari imekamilika. Umelipa TSh {amount}. Asante kwa kutumia Twende!'),
  ('ride.completed.rider', 'en', 'PUSH', null,
   'Trip completed. You paid {currency} {amount}. Thanks for riding with Twende!'),

  -- Subscription expired
  ('subscription.expired.driver', 'sw-TZ', 'PUSH', null,
   'Pakiti yako imeisha. Nunua pakiti mpya ili uendelee kupata abiria.'),
  ('subscription.expired.driver', 'en', 'PUSH', null,
   'Your bundle has expired. Purchase a new bundle to continue receiving rides.'),

  -- Trip OTP (to rider)
  ('trip.otp', 'sw-TZ', 'PUSH', null,
   'Dereva amefika. Mpe code hii: {otp}'),
  ('trip.otp', 'en', 'PUSH', null,
   'Your driver has arrived. Share code {otp} to start your trip.'),

  -- OTP resend
  ('trip.otp.resend', 'sw-TZ', 'PUSH', null,
   'Code mpya ya safari: {otp}'),
  ('trip.otp.resend', 'en', 'PUSH', null,
   'New trip code: {otp}'),

  -- Rejection counter nudge (to rider, after 3 rejections)
  ('ride.rejection.nudge', 'sw-TZ', 'PUSH', null,
   'Madereva {count} walipita. Ongeza bei ili kupata haraka zaidi.'),
  ('ride.rejection.nudge', 'en', 'PUSH', null,
   '{count} drivers passed. Boost your fare to get picked up faster.'),

  -- New ride offer (to driver)
  ('driver.offer', 'sw-TZ', 'PUSH', null,
   'Safari mpya: {distanceKm} km mbali. TSh {fare}. Kukubali?'),
  ('driver.offer', 'en', 'PUSH', null,
   'New ride {distanceKm} km away — {currency} {fare}. Accept?'),

  -- Ride taken by another driver
  ('driver.offer.taken', 'sw-TZ', 'PUSH', null,
   'Safari hiyo imechukuliwa na dereva mwingine.'),
  ('driver.offer.taken', 'en', 'PUSH', null,
   'That ride was taken by another driver.');
```

---

## 4. Channels

| Channel | Provider | Used for |
|---|---|---|
| PUSH (FCM) | Firebase Cloud Messaging | Ride status updates, driver match, OTP, payment confirmation |
| SMS | Africa's Talking (TZ), Twilio (KE stub) | OTP delivery, ride confirmation, driver approval |
| IN_APP | DB + WebSocket signal | Notification inbox, rejection counter updates |
| EMAIL | SendGrid | Driver approval, receipts, account changes |

---

## 5. Provider Abstraction (CRITICAL)

### SmsProvider Interface

```java
public interface SmsProvider {
    String getId();  // "africastalking", "twilio"
    void sendSms(String phoneNumber, String message);
    boolean supportsCountry(String countryCode);
}
```

### PushProvider Interface

```java
public interface PushProvider {
    String getId();  // "fcm", "onesignal"
    void sendNotification(UUID userId, String title, String body, Map<String, String> data);
    void sendData(UUID userId, Map<String, String> data);
}
```

### Per-Country Provider Switching

`CountryConfig.smsProvider` and `CountryConfig.pushProvider` determine which implementation
handles notifications for that country. Tanzania uses Africa's Talking for SMS and FCM for push.
Kenya could use Twilio or Beem for SMS, OneSignal for push. Changing a provider for one
country must NOT affect other countries.

### NotificationService Delegation

```java
@Service
public class NotificationService {
    private final Map<String, SmsProvider> smsProviders;     // keyed by getId()
    private final Map<String, PushProvider> pushProviders;   // keyed by getId()
    private final CountryConfigClient countryConfigClient;

    public void sendSms(String countryCode, String phoneNumber, String message) {
        if (devMode) { log.info("DEV SMS to {}: {}", phoneNumber, message); return; }
        CountryConfigDto config = countryConfigClient.getConfig(countryCode);
        SmsProvider provider = smsProviders.get(config.getSmsProvider().toLowerCase());
        provider.sendSms(phoneNumber, message);
        logNotification(/* ... */);
    }

    public void sendPush(String countryCode, UUID userId, String title, String body, Map<String, String> data) {
        CountryConfigDto config = countryConfigClient.getConfig(countryCode);
        PushProvider provider = pushProviders.get(config.getPushProvider().toLowerCase());
        provider.sendNotification(userId, title, body, data);
        logNotification(/* ... */);
    }

    public void sendPushData(String countryCode, UUID userId, Map<String, String> data) {
        CountryConfigDto config = countryConfigClient.getConfig(countryCode);
        PushProvider provider = pushProviders.get(config.getPushProvider().toLowerCase());
        provider.sendData(userId, data);
        logNotification(/* ... */);
    }
}
```

### Provider Implementations

**AfricasTalkingSmsProvider** -- calls Africa's Talking REST API via Spring `RestClient`.
No SDK dependency. See root CLAUDE.md for implementation pattern. Supports TZ, KE, UG.

**FcmPushProvider** -- uses Firebase Admin SDK (`com.google.firebase:firebase-admin`).
Reads FCM token from `fcm_tokens` table. Sends both notification payloads and data payloads.

**TwilioSmsProvider** -- stub for Kenya. Implements `SmsProvider`, throws
`UnsupportedOperationException` until wired.

**OneSignalPushProvider** -- stub. Implements `PushProvider`, throws
`UnsupportedOperationException` until wired.

### Firebase Initialization

```java
@Configuration
public class FirebaseConfig {
    @Value("${twende.firebase.service-account-json:}")
    private String serviceAccountJson;

    @PostConstruct
    public void init() {
        if (serviceAccountJson.isBlank()) return;
        FirebaseApp.initializeApp(FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(
                new ByteArrayInputStream(serviceAccountJson.getBytes())))
            .build());
    }
}
```

---

## 6. Push Data vs Notification Payload (IMPORTANT)

This distinction is critical for correct mobile app behavior:

- **`notification` payload:** Shows on lock screen when app is in background. Use for
  human-readable messages: "driver on the way", "trip completed", "subscription expired".
- **`data` payload:** App handles programmatically. Use for structured data the app
  renders: OTP display, live updates, rejection counter.
- **OTP and rejection counter updates:** `data` payload ONLY -- app handles rendering.
- **"Driver on the way", "ride completed":** `notification` payload so it shows on lock screen.

```java
// For OTP: data only
pushProvider.sendData(userId, Map.of(
    "type", "TRIP_OTP",
    "otp", otp,
    "rideId", rideId.toString()
));

// For ride status: notification payload
pushProvider.sendNotification(userId, title, body, Map.of(
    "type", "RIDE_STATUS",
    "rideId", rideId.toString(),
    "status", status
));
```

---

## 7. Template Resolution

```java
public String resolveTemplate(String templateKey, String locale, Map<String, String> params) {
    // 1. Find template for exact locale (e.g. "sw-TZ")
    // 2. Fall back to language only ("sw")
    // 3. Fall back to "en"
    NotificationTemplate template = templateRepo
        .findByKeyAndLocale(templateKey, locale)
        .orElse(templateRepo.findByKeyAndLocale(templateKey, "en").orElseThrow());

    // Replace {placeholders} with params
    String body = template.getBody();
    for (Map.Entry<String, String> entry : params.entrySet()) {
        body = body.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return body;
}
```

Templates are seeded via Flyway migration. Admin can update templates at runtime but this
service treats them as read-only at runtime (no admin endpoints in this service).

---

## 8. Kafka Topics Consumed

| Topic | Handler | Notifications Sent |
|---|---|---|
| `twende.rides.status-updated` | `RideStatusNotificationHandler` | Rider: driver on way, arrived. Driver: ride status changes |
| `twende.rides.completed` | `RideCompletedNotificationHandler` | Rider: receipt. Driver: earnings credited |
| `twende.drivers.approved` | `DriverApprovalNotificationHandler` | Driver: approval SMS + push |
| `twende.drivers.offer-notification` | `DriverOfferNotificationHandler` | Driver: new ride offer with fare details |
| `twende.payments.completed` | `PaymentNotificationHandler` | Rider: payment confirmation |
| `twende.subscriptions.expired` | `SubscriptionNotificationHandler` | Driver: bundle expiry warning |
| `twende.loyalty.free-ride-earned` | `LoyaltyNotificationHandler` | Rider: free ride offer earned |
| `twende.notifications.send` | `DirectNotificationHandler` | Generic direct send from any service |

**Consumer group:** `notification-service-group`

**Event processing rules:**
- All consumers are idempotent -- duplicate events must not send duplicate notifications
- Log every notification attempt to `notification_log` with status SENT, FAILED, or SKIPPED
- On provider failure, log error and set status=FAILED. Do NOT retry inline -- use a
  scheduled retry job for FAILED entries

### Key Event Handlers

**RideStatusNotificationHandler:**
- On `DRIVER_ASSIGNED`: send `ride.assigned.rider` push notification to rider
- On `DRIVER_ARRIVED`: send `trip.otp` data payload to rider (contains OTP code)
- On OTP resend: send `trip.otp.resend` data payload to rider

**DriverOfferNotificationHandler:**
- On `DriverOfferNotificationEvent`: send `driver.offer` push to driver with fare, distance, time details
- On offer taken by another driver: send `driver.offer.taken` push

**RideCompletedNotificationHandler:**
- Send `ride.completed.rider` push to rider with fare amount
- Send earnings notification to driver

**SubscriptionNotificationHandler:**
- Send `subscription.expired.driver` push to driver

**LoyaltyNotificationHandler:**
- On `FreeRideOfferEarnedEvent`: send push to rider about new free ride offer

---

## 9. FCM Token Management

The mobile app registers its FCM token on every launch. Token registration endpoint:

```
POST /api/v1/notifications/fcm-token
{ "token": "fcm-token-string", "platform": "ANDROID" }
```

- Upsert: if `(user_id, token)` exists, update `is_active` and `updated_at`
- When sending push, use the latest active token for the user:
  `fcmTokenRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)`
- Mark tokens as inactive when FCM returns `UNREGISTERED` or `INVALID_ARGUMENT` error

---

## 10. Dev Mode

Set `twende.sms.dev-mode=true` (env var `SMS_DEV_MODE=true`) to log SMS content to console
instead of calling the real SMS provider. Useful for local development and testing.

```java
if (devMode) {
    log.info("DEV SMS to {}: {}", phoneNumber, message);
    return;
}
```

Push notifications in dev mode still call FCM (or can be disabled similarly with a
`twende.push.dev-mode` flag).

---

## 11. Inter-Service Communication

**No Eureka. No Feign. Use Spring `RestClient` for all inter-service calls.**

### Dependencies on Other Services

| Service | What We Get | How |
|---|---|---|
| **country-config-service** | `CountryConfig` (smsProvider, pushProvider, currency) | RestClient, cached in Redis |

This service is primarily a consumer of Kafka events. It makes minimal outbound REST calls --
only to country-config-service to resolve which provider to use for a given country.

No other modules call this service's REST API directly. All notification triggering goes
through Kafka events.

---

## 12. Application Configuration

```yaml
server:
  port: 8091

spring:
  application:
    name: notification-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_notifications
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
      group-id: notification-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.twende.*

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
  push:
    dev-mode: ${PUSH_DEV_MODE:false}
  services:
    country-config-service:
      url: ${COUNTRY_CONFIG_SERVICE_URL:http://localhost:8082}
  firebase:
    service-account-json: ${FIREBASE_SERVICE_ACCOUNT_JSON:}
  africastalking:
    api-key: ${AT_API_KEY:}
    username: ${AT_USERNAME:sandbox}
    sender-id: ${AT_SENDER_ID:TWENDE}
    base-url: https://api.africastalking.com/version1
  sendgrid:
    api-key: ${SENDGRID_API_KEY:}
    from-email: ${SENDGRID_FROM_EMAIL:noreply@twende.co.tz}

logging:
  level:
    com.twende: DEBUG
    org.springframework.kafka: WARN
```

---

## 13. Authentication and Authorization

This service does NOT validate JWTs directly. The API gateway handles JWT validation and
forwards identity via headers:

| Header | Description |
|--------|-------------|
| `X-User-Id` | UUID of the authenticated user |
| `X-User-Role` | `RIDER`, `DRIVER`, or `ADMIN` |
| `X-Country-Code` | Two-letter country code (e.g., `TZ`) |

**Endpoint security rules:**
- `POST /api/v1/notifications/fcm-token` -- requires authenticated user (any role)
- `/actuator/health` -- public
- No admin endpoints in this service
- No internal endpoints -- all triggering via Kafka

---

## 14. Conventions

**These apply to every file in this service. Never deviate.**

- All entities extend `BaseEntity` from common-lib (ULID-based UUID PK, createdAt, updatedAt, countryCode)
- Money fields use `BigDecimal` -- never `double` or `float`
- Timestamps use `Instant` -- never `LocalDateTime` or `Date`
- All controller methods return `ApiResponse<T>` from common-lib
- Validate all incoming requests with `@Valid @RequestBody`
- No cross-service repository access -- use RestClient to call other services' internal APIs
- No Feign, no Eureka, no Spring Cloud -- use Spring `RestClient` for inter-service calls
- No modules call SMS/push providers directly -- all notification sending goes through
  `NotificationService` in this module
- SMS and push provider switching is per-country -- changing a provider for one country
  must not affect other countries
- AfricasTalkingSmsProvider uses RestClient -- NO Africa's Talking SDK dependency
- MapStruct for entity-to-DTO mapping
- Lombok for boilerplate reduction (`@Getter @Setter @NoArgsConstructor` on entities)
- All Kafka consumers are idempotent
- Every notification attempt is logged to `notification_log`

---

## 15. Testing

### Unit Tests

- `TemplateResolver` -- locale fallback chain (exact -> language -> en), placeholder replacement
- `NotificationService` -- correct provider delegation based on country config
- `AfricasTalkingSmsProvider` -- request body format, URL encoding
- `FcmPushProvider` -- notification vs data payload construction
- Dev mode -- SMS logged, not sent

### Integration Tests

- Use Testcontainers for PostgreSQL, Redis, and Kafka
- Test end-to-end: publish Kafka event -> handler processes -> notification logged in DB
- Test FCM token registration and deactivation
- Test template resolution with seeded templates
- Mock external providers (Africa's Talking, FCM) with WireMock or MockRestServiceServer

### Test Naming

```java
@Test
void givenRideAssignedEvent_whenProcessed_thenPushSentToRider() { ... }

@Test
void givenTanzaniaCountryCode_whenSendSms_thenAfricasTalkingProviderUsed() { ... }

@Test
void givenDevModeEnabled_whenSendSms_thenLoggedNotSent() { ... }

@Test
void givenSwahiliLocale_whenResolveTemplate_thenSwahiliBodyReturned() { ... }

@Test
void givenUnknownLocale_whenResolveTemplate_thenFallsBackToEnglish() { ... }
```

### Coverage

- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw verify`
- Report: `target/site/jacoco/index.html`
- Excluded from coverage: entities, DTOs, enums, config classes, `NotificationServiceApplication`

---

## Implementation Steps

- [ ] 1. `application.yml` -- port 8091, datasource `twende_notifications`, Kafka (`consumer.group-id: notification-service-group`), Firebase config (`service-account-json`), Africa's Talking config (`api-key`, `username`, `sender-id`, `base-url`), SendGrid config (`api-key`, `from-email`), `SMS_DEV_MODE=true`, `PUSH_DEV_MODE=false`, country-config-service URL
- [ ] 2. Entities: `NotificationTemplate` (templateKey, locale, channel, subject, body), `FcmToken` (userId, token, platform, isActive), `NotificationLog` (userId, countryCode, channel, templateKey, title, body, status, provider, providerRef, error, sentAt) -- all extend `BaseEntity`
- [ ] 3. Repositories: `NotificationTemplateRepository` (`findByKeyAndLocale`), `FcmTokenRepository` (`findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc`), `NotificationLogRepository`
- [ ] 4. `SmsProvider` interface (`getId`, `sendSms`, `supportsCountry`) + `AfricasTalkingSmsProvider` (RestClient to AT REST API, NO SDK, URL-encoded form body) + `TwilioSmsProvider` (stub, throws `UnsupportedOperationException`)
- [ ] 5. `PushProvider` interface (`getId`, `sendNotification`, `sendData`) + `FcmPushProvider` (Firebase Admin SDK, reads token from DB, handles notification vs data payloads) + `OneSignalPushProvider` (stub)
- [ ] 6. `FirebaseConfig`: `@PostConstruct` initialization from `service-account-json`, skip if blank
- [ ] 7. `TemplateResolver`: resolve by `templateKey + locale` with fallback chain (exact locale -> language only -> `en`), replace `{placeholders}` with params
- [ ] 8. `NotificationService`: `sendSms`, `sendPush`, `sendPushData`, `sendEmail` -- delegate to providers based on country config (fetched via `CountryConfigClient` RestClient call, cached in Redis), log every attempt to `notification_log`
- [ ] 9. `CountryConfigClient`: RestClient to country-config-service for `smsProvider` and `pushProvider` resolution per country
- [ ] 10. Kafka consumers (`NotificationEventConsumer` + handlers): `twende.rides.status-updated` (rider push on DRIVER_ASSIGNED/ARRIVED), `twende.rides.completed` (receipt push), `twende.drivers.approved` (SMS + push), `twende.drivers.offer-notification` (driver offer push), `twende.payments.completed` (payment confirmation), `twende.subscriptions.expired` (bundle expiry push), `twende.loyalty.free-ride-earned` (free ride offer push), `twende.notifications.send` (generic direct send) -- all idempotent
- [ ] 11. `FcmTokenController`: `POST /api/v1/notifications/fcm-token` -- upsert `(userId, token)`, mark inactive tokens on FCM UNREGISTERED errors
- [ ] 12. Flyway migrations: `V1__create_notifications_schema.sql` (tables + indexes), `V2__seed_notification_templates.sql` (Swahili + English templates for all events)
- [ ] 13. Dockerfile — Multi-stage build (eclipse-temurin:21-jdk-alpine for build, 21-jre-alpine for run). Non-root `twende` user. Health check on `/actuator/health`. Expose port 8091.
- [ ] 14. OpenAPI config — `OpenApiConfig.java` with SpringDoc `OpenAPI` bean. Title: "Notification Service API". Swagger UI at `/swagger-ui.html`.
- [ ] 15. Unit tests + integration tests (Testcontainers for PostgreSQL, Redis, Kafka; WireMock for Africa's Talking and country-config-service; mock Firebase), verify >= 80% coverage with `./mvnw verify`
