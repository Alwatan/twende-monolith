# CLAUDE.md — Analytics Service

> Business intelligence service for the Twende ride-hailing platform.
> Read this fully before writing any code in this module.

---

## 1. What This Service Does

The analytics service is the platform's event sink. It consumes all significant Kafka
events across the platform and stores them in an append-only event store. It provides
dashboards for driver earnings, trip statistics, and admin-level business metrics.
Materialized summary tables are refreshed nightly for efficient querying.

**Port:** 8093
**Database:** `twende_analytics`

---

## 2. API Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/analytics/driver/earnings` | Driver earnings breakdown (daily/weekly/monthly) |
| `GET` | `/api/v1/analytics/driver/trips` | Trip statistics for the calling driver |
| `GET` | `/api/v1/analytics/admin/overview` | Platform-wide KPIs (ADMIN only) |
| `GET` | `/api/v1/analytics/admin/countries/{code}` | Per-country metrics (ADMIN only) |

### Driver Earnings Response

```json
GET /api/v1/analytics/driver/earnings?period=WEEKLY
{
  "success": true,
  "data": {
    "driverId": "uuid",
    "period": "WEEKLY",
    "totalEarned": 85000,
    "currencyCode": "TZS",
    "displayTotal": "TSh 85,000",
    "tripCount": 42,
    "onlineHours": 38.5,
    "dailyBreakdown": [
      { "date": "2026-04-01", "earned": 12000, "trips": 6, "onlineHours": 5.5 },
      { "date": "2026-04-02", "earned": 14500, "trips": 7, "onlineHours": 6.0 }
    ]
  }
}
```

### Driver Trip Stats Response

```json
GET /api/v1/analytics/driver/trips?from=2026-04-01&to=2026-04-07
{
  "success": true,
  "data": {
    "totalTrips": 42,
    "completedTrips": 40,
    "cancelledTrips": 2,
    "totalDistanceKm": 320.5,
    "averageTripDistanceKm": 8.0,
    "averageTripDurationMinutes": 15,
    "averageRating": 4.7,
    "byVehicleType": {
      "BAJAJ": { "trips": 25, "earned": 50000 },
      "CAR_ECONOMY": { "trips": 17, "earned": 35000 }
    }
  }
}
```

### Admin Overview Response

```json
GET /api/v1/analytics/admin/overview
{
  "success": true,
  "data": {
    "totalRides": 15420,
    "totalDrivers": 320,
    "activeDrivers": 185,
    "totalRiders": 8900,
    "activeSubscriptions": 210,
    "revenueFromSubscriptions": 4200000,
    "currencyCode": "TZS",
    "periodStart": "2026-04-01",
    "periodEnd": "2026-04-07"
  }
}
```

### Per-Country Metrics Response

```json
GET /api/v1/analytics/admin/countries/TZ
{
  "success": true,
  "data": {
    "countryCode": "TZ",
    "totalRides": 15420,
    "completedRides": 14800,
    "cancelledRides": 620,
    "averageFare": 3500,
    "totalFareVolume": 51800000,
    "newDrivers": 25,
    "newRiders": 340,
    "topVehicleType": "BAJAJ",
    "periodStart": "2026-04-01",
    "periodEnd": "2026-04-07"
  }
}
```

---

## 3. Package Structure

```
tz.co.twende.analytics
├── AnalyticsServiceApplication.java
├── config/
│   ├── KafkaConfig.java             # Consumer config for all platform events
│   ├── JpaConfig.java               # @EnableJpaAuditing
│   ├── SchedulingConfig.java        # @EnableScheduling for nightly summaries
│   └── RedisConfig.java             # Optional: cache for dashboard queries
├── entity/
│   ├── AnalyticsEvent.java          # Append-only event store entity
│   └── DriverDailySummary.java      # Materialized daily summary entity
├── repository/
│   ├── AnalyticsEventRepository.java
│   └── DriverDailySummaryRepository.java
├── service/
│   ├── EventIngestionService.java   # Writes events to the store
│   ├── DriverAnalyticsService.java  # Driver earnings and trip queries
│   ├── AdminAnalyticsService.java   # Platform KPIs and country metrics
│   └── SummaryRefreshService.java   # Nightly materialized view refresh
├── controller/
│   ├── DriverAnalyticsController.java
│   └── AdminAnalyticsController.java
├── dto/
│   ├── DriverEarningsDto.java
│   ├── DriverTripStatsDto.java
│   ├── AdminOverviewDto.java
│   ├── CountryMetricsDto.java
│   └── DailyBreakdownDto.java
└── kafka/
    └── AnalyticsEventConsumer.java   # Listens to all consumed topics
```

---

## 4. Database Schema

```sql
-- V1__create_analytics_schema.sql

-- Append-only event store
CREATE TABLE analytics_events (
    id           UUID        PRIMARY KEY,
    event_type   VARCHAR(50) NOT NULL,
    country_code CHAR(2)     NOT NULL,
    actor_id     UUID,
    payload      JSONB       NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_type    ON analytics_events(event_type, occurred_at DESC);
CREATE INDEX idx_events_actor   ON analytics_events(actor_id, occurred_at DESC);
CREATE INDEX idx_events_country ON analytics_events(country_code, occurred_at DESC);

-- Consider enabling TimescaleDB extension for time-series optimisation:
-- SELECT create_hypertable('analytics_events', 'occurred_at');

-- Materialised daily summaries (refreshed nightly by @Scheduled job)
CREATE TABLE driver_daily_summaries (
    id           UUID        PRIMARY KEY,
    driver_id    UUID        NOT NULL,
    country_code CHAR(2)     NOT NULL,
    date         DATE        NOT NULL,
    trip_count   INT         NOT NULL DEFAULT 0,
    total_earned NUMERIC(12,2) NOT NULL DEFAULT 0,
    online_hours NUMERIC(5,2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(driver_id, date)
);

CREATE INDEX idx_driver_summaries_country ON driver_daily_summaries(country_code, date DESC);
CREATE INDEX idx_driver_summaries_driver  ON driver_daily_summaries(driver_id, date DESC);
```

---

## 5. Entities

### AnalyticsEvent (append-only)

```java
@Entity
@Table(name = "analytics_events")
@Getter @Setter @NoArgsConstructor
public class AnalyticsEvent extends BaseEntity {
    @Column(nullable = false, length = 50)
    private String eventType;

    private UUID actorId;

    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;  // stored as JSON string, cast to JSONB by Hibernate

    @Column(nullable = false)
    private Instant occurredAt;
}
```

**Important:** This table is append-only. Events are never updated or deleted. The
`payload` column stores the full Kafka event payload as JSONB for flexible querying.

### DriverDailySummary

```java
@Entity
@Table(name = "driver_daily_summaries")
@Getter @Setter @NoArgsConstructor
public class DriverDailySummary extends BaseEntity {
    @Column(nullable = false)
    private UUID driverId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int tripCount;

    @Column(nullable = false)
    private BigDecimal totalEarned;  // always BigDecimal for money

    @Column(nullable = false)
    private BigDecimal onlineHours;
}
```

---

## 6. Kafka Topics

### Consumed

| Topic | Consumer Group | Action |
|---|---|---|
| `twende.rides.completed` | `twende-analytics` | Store event; extract fare for earnings |
| `twende.rides.cancelled` | `twende-analytics` | Store event; track cancellation metrics |
| `twende.payments.completed` | `twende-analytics` | Store event; track payment volume |
| `twende.subscriptions.activated` | `twende-analytics` | Store event; track subscription revenue |
| `twende.users.registered` | `twende-analytics` | Store event; track new user growth |
| `twende.drivers.approved` | `twende-analytics` | Store event; track driver onboarding |
| `twende.ratings.submitted` | `twende-analytics` | Store event; track rating distribution |

### Not Published

The analytics service does not publish Kafka events. It is a terminal consumer.

---

## 7. Event Ingestion

All consumed Kafka events follow the same ingestion pattern:

```java
@KafkaListener(topics = {
    "twende.rides.completed",
    "twende.rides.cancelled",
    "twende.payments.completed",
    "twende.subscriptions.activated",
    "twende.users.registered",
    "twende.drivers.approved",
    "twende.ratings.submitted"
}, groupId = "${spring.application.name}")
public void onEvent(KafkaEvent event) {
    AnalyticsEvent analyticsEvent = new AnalyticsEvent();
    analyticsEvent.setEventType(event.getEventType());
    analyticsEvent.setCountryCode(event.getCountryCode());
    analyticsEvent.setActorId(resolveActorId(event));
    analyticsEvent.setPayload(objectMapper.writeValueAsString(event));
    analyticsEvent.setOccurredAt(event.getTimestamp());
    eventRepository.save(analyticsEvent);
}
```

**Actor resolution:** For ride events, the actor is the driver. For user events, the
actor is the user. For payment/subscription events, the actor is the driver paying.

---

## 8. Nightly Summary Refresh

A `@Scheduled` job runs at 2:00 AM UTC daily to refresh `driver_daily_summaries`:

```java
@Scheduled(cron = "0 0 2 * * *")  // 2 AM UTC daily
public void refreshDailySummaries() {
    LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);

    // Query analytics_events for yesterday's RIDE_COMPLETED events
    // Group by driver_id, compute:
    //   trip_count  = COUNT(*)
    //   total_earned = SUM(payload->>'finalFare')
    //   online_hours = computed from driver status change events

    // Upsert into driver_daily_summaries
}
```

**Upsert strategy:** Use `ON CONFLICT (driver_id, date) DO UPDATE` to handle reruns
safely. The job is idempotent.

**Online hours calculation:** Derived from pairs of DRIVER_STATUS_UPDATED events
(ONLINE_AVAILABLE -> OFFLINE transitions) for the day. If a driver never goes offline,
cap at 24 hours.

---

## 9. Query Strategy

### Driver Earnings

Query `driver_daily_summaries` for the requested period. Aggregate in the database:

```java
@Query("SELECT SUM(s.totalEarned), SUM(s.tripCount), SUM(s.onlineHours) " +
       "FROM DriverDailySummary s " +
       "WHERE s.driverId = :driverId AND s.date BETWEEN :from AND :to")
```

For daily breakdown, return individual summary rows.

### Admin Overview

Query `analytics_events` with aggregate functions:

```sql
SELECT event_type, COUNT(*), country_code
FROM analytics_events
WHERE occurred_at BETWEEN ? AND ?
GROUP BY event_type, country_code;
```

For subscription revenue, sum payments where `event_type = 'SUBSCRIPTION_ACTIVATED'`
and extract the amount from the JSONB payload.

### TimescaleDB Consideration

If the `analytics_events` table grows beyond 10 million rows, enable TimescaleDB:

```sql
CREATE EXTENSION IF NOT EXISTS timescaledb;
SELECT create_hypertable('analytics_events', 'occurred_at');
```

This enables efficient time-range queries, automatic partitioning, and compression.
Do not enable until needed -- standard PostgreSQL with proper indexes handles the
initial scale.

---

## 10. Configuration

```yaml
server:
  port: 8093

spring:
  application:
    name: twende-analytics
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_analytics
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
    hikari:
      maximum-pool-size: 15
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
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
    consumer:
      group-id: ${spring.application.name}
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: tz.co.twende.*

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

logging:
  level:
    tz.co.twende: DEBUG
    org.springframework.security: WARN
```

---

## 11. Key Implementation Rules

1. **Append-only event store** -- never update or delete rows in `analytics_events`.
   This table is an audit-grade event log. All writes are inserts.

2. **BigDecimal for all monetary values** -- earnings, fare amounts, subscription
   costs must use `BigDecimal` in Java and `NUMERIC(12,2)` in the database.

3. **Idempotent summary refresh** -- the nightly job must be safe to rerun. Use
   `ON CONFLICT DO UPDATE` (upsert) for `driver_daily_summaries`.

4. **Admin endpoints require ADMIN role** -- check `X-User-Role` header equals
   `ADMIN` for `/admin/**` endpoints.

5. **Driver endpoints use X-User-Id** -- the calling driver's identity comes from
   the `X-User-Id` header injected by the API Gateway.

6. **No Eureka, no Feign** -- this service does not call other services synchronously.
   All data arrives via Kafka events.

7. **All responses use `ApiResponse<T>` wrapper** -- consistent with the platform-wide
   response format.

8. **Kafka key format** -- consumer group is `twende-analytics`. Messages are keyed
   by `{countryCode}:{entityId}`.

9. **JSONB payload queries** -- use PostgreSQL's JSONB operators (`->>`, `@>`) for
   querying event payloads when materialized summaries are insufficient.

10. **No blocking on ingestion** -- Kafka consumers must not call external services.
    Event ingestion should be a fast insert-only operation.

11. **Date ranges default to last 7 days** -- if `from`/`to` parameters are omitted
    on query endpoints, default to the last 7 days.

---

## 12. Testing

### Unit Tests

- `EventIngestionService` -- verify correct mapping from Kafka events to
  `AnalyticsEvent` entities for each event type
- `DriverAnalyticsService` -- test earnings aggregation logic with various
  daily summary combinations
- `AdminAnalyticsService` -- test overview KPI computation
- `SummaryRefreshService` -- test idempotent upsert logic

### Integration Tests

- Use Testcontainers for PostgreSQL and Kafka
- Test end-to-end: publish Kafka event -> verify `analytics_events` row created
  with correct JSONB payload
- Test nightly summary refresh: insert events -> run refresh -> verify
  `driver_daily_summaries` rows
- Test driver earnings endpoint: seed summaries -> call endpoint -> verify response
- Test admin overview with multiple countries

### Test Naming

```java
@Test
void givenRideCompletedEvent_whenConsumed_thenAnalyticsEventStored() { ... }

@Test
void givenMultipleDailySummaries_whenDriverEarningsQueried_thenCorrectTotalsReturned() { ... }

@Test
void givenSummaryAlreadyExists_whenNightlyRefreshRuns_thenSummaryUpdatedNotDuplicated() { ... }

@Test
void givenNonAdminUser_whenAdminOverviewRequested_thenForbidden() { ... }
```

### Coverage

- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw -pl analytics-service verify`
- Excluded from coverage: DTOs, enums, config classes

---

## Implementation Steps

- [ ] 1. `application.yml` -- port 8093, datasource `twende_analytics`, Redis (optional dashboard cache), Kafka (`consumer.group-id: twende-analytics`), JPA validate, Flyway enabled
- [ ] 2. Entities: `AnalyticsEvent` (eventType, actorId, payload JSONB, occurredAt) extends `BaseEntity`; `DriverDailySummary` (driverId, date, tripCount, totalEarned BigDecimal, onlineHours BigDecimal) extends `BaseEntity` with UNIQUE(driverId, date)
- [ ] 3. Repositories: `AnalyticsEventRepository` (queries by eventType, countryCode, actorId, date ranges), `DriverDailySummaryRepository` (aggregate queries for earnings, `@Query` SUM by driverId + date range, upsert support)
- [ ] 4. `EventIngestionService`: map Kafka event to `AnalyticsEvent` entity (resolve actorId per event type), save -- fast insert-only, no external calls
- [ ] 5. Kafka consumers: `AnalyticsEventConsumer` with `@KafkaListener` on all 7 topics (`twende.rides.completed`, `twende.rides.cancelled`, `twende.payments.completed`, `twende.subscriptions.activated`, `twende.users.registered`, `twende.drivers.approved`, `twende.ratings.submitted`) -- delegate to `EventIngestionService`
- [ ] 6. `SummaryRefreshService`: `@Scheduled(cron = "0 0 2 * * *")` nightly at 2 AM UTC, query yesterday's RIDE_COMPLETED events, group by driverId, compute tripCount + totalEarned + onlineHours, upsert into `driver_daily_summaries` (`ON CONFLICT DO UPDATE`)
- [ ] 7. `DriverAnalyticsService`: query `driver_daily_summaries` for earnings breakdown (daily/weekly/monthly), trip stats with date range filtering, default to last 7 days
- [ ] 8. `AdminAnalyticsService`: platform-wide KPIs from `analytics_events` (totalRides, totalDrivers, activeSubscriptions, revenue), per-country metrics using JSONB queries
- [ ] 9. `DriverAnalyticsController` (GET `/api/v1/analytics/driver/earnings`, GET `/api/v1/analytics/driver/trips`) + `AdminAnalyticsController` (GET `/api/v1/analytics/admin/overview`, GET `/api/v1/analytics/admin/countries/{code}` -- ADMIN role required)
- [ ] 10. Flyway migration: `V1__create_analytics_schema.sql` (both tables + all indexes)
- [ ] 11. Dockerfile — Multi-stage build (eclipse-temurin:21-jdk-alpine for build, 21-jre-alpine for run). Non-root `twende` user. Health check on `/actuator/health`. Expose port 8093.
- [ ] 12. OpenAPI config — `OpenApiConfig.java` with SpringDoc `OpenAPI` bean. Title: "Analytics Service API". Swagger UI at `/swagger-ui.html`.
- [ ] 13. Unit tests + integration tests (Testcontainers for PostgreSQL and Kafka), verify >= 80% coverage with `./mvnw -pl analytics-service verify`
