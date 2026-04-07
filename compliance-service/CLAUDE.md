# CLAUDE.md — Compliance Service

> Regulatory reporting and audit logging for the Twende ride-hailing platform.
> Read this fully before writing any code in this module.

---

## 1. What This Service Does

The compliance service handles regulatory obligations for each country Twende operates
in. For Tanzania, this means reporting completed trips to SUMATRA (Surface and Marine
Transport Regulatory Authority). The architecture uses a per-country adapter pattern so
adding NTSA (Kenya) or KCCA (Uganda) compliance requires only a new adapter class, not
restructuring the service.

Additionally, the service maintains an audit log of all significant platform events for
regulatory audit trails.

**Port:** 8094
**Database:** `twende_compliance`

---

## 2. API Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/compliance/reports` | List trip reports with filtering (ADMIN) |
| `GET` | `/api/v1/compliance/reports/{id}` | Get specific trip report (ADMIN) |
| `GET` | `/api/v1/compliance/reports/stats` | Submission statistics by country (ADMIN) |
| `POST` | `/api/v1/compliance/reports/retry` | Retry failed submissions for a country (ADMIN) |
| `GET` | `/api/v1/compliance/audit-log` | Query audit log with filters (ADMIN) |

### Trip Reports List Response

```json
GET /api/v1/compliance/reports?countryCode=TZ&submitted=false&page=0&size=20
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "rideId": "uuid",
        "countryCode": "TZ",
        "driverId": "uuid",
        "riderId": "uuid",
        "vehicleType": "BAJAJ",
        "pickupLat": -6.7728,
        "pickupLng": 39.2310,
        "dropoffLat": -6.8160,
        "dropoffLng": 39.2803,
        "distanceMetres": 8200,
        "durationSeconds": 900,
        "fare": 3500.00,
        "currency": "TZS",
        "startedAt": "2026-04-07T08:00:00Z",
        "completedAt": "2026-04-07T08:15:00Z",
        "submitted": false,
        "submittedAt": null,
        "submissionRef": null,
        "submissionError": "Connection timeout",
        "createdAt": "2026-04-07T08:15:01Z"
      }
    ],
    "totalElements": 45,
    "totalPages": 3,
    "page": 0,
    "size": 20
  }
}
```

### Submission Stats Response

```json
GET /api/v1/compliance/reports/stats
{
  "success": true,
  "data": [
    {
      "countryCode": "TZ",
      "totalReports": 15420,
      "submitted": 15350,
      "pending": 45,
      "failed": 25,
      "lastSubmissionAt": "2026-04-07T11:00:00Z"
    }
  ]
}
```

### Audit Log Response

```json
GET /api/v1/compliance/audit-log?countryCode=TZ&eventType=RIDE_COMPLETED&from=2026-04-01
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "countryCode": "TZ",
        "eventType": "RIDE_COMPLETED",
        "entityId": "uuid",
        "actorId": "uuid",
        "payload": { "rideId": "...", "fare": 3500, "driverId": "..." },
        "occurredAt": "2026-04-07T08:15:00Z"
      }
    ],
    "totalElements": 500,
    "totalPages": 25,
    "page": 0,
    "size": 20
  }
}
```

All endpoints require ADMIN role (check `X-User-Role` header).

---

## 3. Package Structure

```
com.twende.compliance
├── ComplianceServiceApplication.java
├── config/
│   ├── KafkaConfig.java             # Consumer config for all platform events
│   ├── JpaConfig.java               # @EnableJpaAuditing
│   └── SchedulingConfig.java        # @EnableScheduling for batch submission
├── entity/
│   ├── TripReport.java              # Trip report for regulatory submission
│   └── AuditLog.java                # Append-only audit trail
├── repository/
│   ├── TripReportRepository.java
│   └── AuditLogRepository.java
├── service/
│   ├── ComplianceService.java       # Orchestrates report creation and submission
│   ├── AuditService.java            # Writes audit log entries
│   └── BatchSubmissionService.java  # @Scheduled hourly submission
├── adapter/
│   ├── ComplianceAdapter.java       # Interface -- one per country
│   ├── SumatraAdapter.java          # Tanzania: SUMATRA API via RestClient
│   ├── NtsaAdapter.java             # Kenya: stub (Phase 2)
│   └── KccaAdapter.java             # Uganda: stub (Phase 3)
├── controller/
│   ├── ComplianceController.java    # Trip reports endpoints
│   └── AuditLogController.java      # Audit log query endpoint
├── dto/
│   ├── TripReportDto.java
│   ├── TripReportFilterDto.java
│   ├── SubmissionStatsDto.java
│   └── AuditLogDto.java
├── exception/
│   └── ComplianceException.java     # Adapter submission failures
└── kafka/
    └── ComplianceEventConsumer.java  # Listens to all consumed topics
```

---

## 4. Database Schema

```sql
-- V1__create_compliance_schema.sql

CREATE TABLE trip_reports (
    id               UUID        PRIMARY KEY,
    country_code     CHAR(2)     NOT NULL,
    ride_id          UUID        NOT NULL UNIQUE,
    driver_id        UUID        NOT NULL,
    rider_id         UUID        NOT NULL,
    vehicle_type     VARCHAR(30) NOT NULL,
    pickup_lat       NUMERIC(10,7) NOT NULL,
    pickup_lng       NUMERIC(10,7) NOT NULL,
    dropoff_lat      NUMERIC(10,7) NOT NULL,
    dropoff_lng      NUMERIC(10,7) NOT NULL,
    distance_metres  INT,
    duration_seconds INT,
    fare             NUMERIC(12,2),
    currency         VARCHAR(3),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,

    -- Submission tracking
    submitted        BOOLEAN     NOT NULL DEFAULT false,
    submitted_at     TIMESTAMPTZ,
    submission_ref   VARCHAR(200),
    submission_error TEXT,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_reports_pending ON trip_reports(country_code, submitted, created_at)
    WHERE submitted = false;
CREATE INDEX idx_trip_reports_country ON trip_reports(country_code, created_at DESC);
CREATE INDEX idx_trip_reports_driver  ON trip_reports(driver_id, created_at DESC);

CREATE TABLE audit_log (
    id           UUID        PRIMARY KEY,
    country_code CHAR(2)     NOT NULL,
    event_type   VARCHAR(50) NOT NULL,
    entity_id    UUID,
    actor_id     UUID,
    payload      JSONB,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_country_type ON audit_log(country_code, event_type, occurred_at DESC);
CREATE INDEX idx_audit_log_entity       ON audit_log(entity_id, occurred_at DESC);
CREATE INDEX idx_audit_log_actor        ON audit_log(actor_id, occurred_at DESC);
```

---

## 5. Entities

### TripReport

```java
@Entity
@Table(name = "trip_reports")
@Getter @Setter @NoArgsConstructor
public class TripReport extends BaseEntity {
    @Column(nullable = false, unique = true)
    private UUID rideId;

    @Column(nullable = false)
    private UUID driverId;

    @Column(nullable = false)
    private UUID riderId;

    @Column(nullable = false, length = 30)
    private String vehicleType;

    @Column(nullable = false)
    private BigDecimal pickupLat;

    @Column(nullable = false)
    private BigDecimal pickupLng;

    @Column(nullable = false)
    private BigDecimal dropoffLat;

    @Column(nullable = false)
    private BigDecimal dropoffLng;

    private Integer distanceMetres;
    private Integer durationSeconds;
    private BigDecimal fare;        // always BigDecimal for money
    private String currency;
    private Instant startedAt;
    private Instant completedAt;

    @Column(nullable = false)
    private boolean submitted = false;

    private Instant submittedAt;

    @Column(length = 200)
    private String submissionRef;

    private String submissionError;
}
```

### AuditLog

```java
@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(generator = "ulid")
    @GenericGenerator(name = "ulid", type = UlidGenerator.class)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 50)
    private String eventType;

    private UUID entityId;
    private UUID actorId;

    @Column(columnDefinition = "JSONB")
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;
}
```

**Note:** `AuditLog` does not extend `BaseEntity` because it has no `updatedAt` field
(append-only, never modified). It uses `UlidGenerator` directly for the ID.

---

## 6. Compliance Adapter Pattern

The adapter pattern enables per-country regulatory reporting without code changes
in the core service logic.

### Interface

```java
public interface ComplianceAdapter {
    /**
     * Returns the ISO 3166-1 alpha-2 country code this adapter handles.
     */
    String getCountryCode();

    /**
     * Submit a single trip report to the country's regulatory authority.
     * @throws ComplianceException if submission fails (will be retried in next batch)
     */
    void submitTripReport(TripReport report) throws ComplianceException;

    /**
     * Whether this country requires trip reporting.
     */
    boolean isTripReportingRequired();
}
```

### SumatraAdapter (Tanzania)

```java
@Component
public class SumatraAdapter implements ComplianceAdapter {
    private final RestClient restClient;

    public SumatraAdapter(
            @Value("${twende.compliance.sumatra.base-url}") String baseUrl,
            @Value("${twende.compliance.sumatra.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public String getCountryCode() { return "TZ"; }

    @Override
    public boolean isTripReportingRequired() { return true; }

    @Override
    public void submitTripReport(TripReport report) throws ComplianceException {
        try {
            SumatraResponse response = restClient.post()
                .uri("/trips")
                .body(toSumatraPayload(report))
                .retrieve()
                .body(SumatraResponse.class);
            report.setSubmissionRef(response.getReferenceNumber());
        } catch (Exception e) {
            throw new ComplianceException("SUMATRA submission failed: " + e.getMessage(), e);
        }
    }

    private SumatraTripPayload toSumatraPayload(TripReport report) {
        // Map TripReport fields to SUMATRA's expected format
        // Include: driver ID, vehicle type, pickup/dropoff coordinates,
        // distance, duration, fare, timestamps
    }
}
```

### Future Adapters (stubs)

```java
@Component
public class NtsaAdapter implements ComplianceAdapter {
    @Override public String getCountryCode() { return "KE"; }
    @Override public boolean isTripReportingRequired() { return false; }  // not yet
    @Override public void submitTripReport(TripReport report) {
        throw new UnsupportedOperationException("NTSA integration not yet implemented");
    }
}

@Component
public class KccaAdapter implements ComplianceAdapter {
    @Override public String getCountryCode() { return "UG"; }
    @Override public boolean isTripReportingRequired() { return false; }
    @Override public void submitTripReport(TripReport report) {
        throw new UnsupportedOperationException("KCCA integration not yet implemented");
    }
}
```

### Adapter Resolution

```java
@Service
public class ComplianceService {
    private final Map<String, ComplianceAdapter> adapters;

    public ComplianceService(List<ComplianceAdapter> adapterList) {
        this.adapters = adapterList.stream()
            .collect(Collectors.toMap(ComplianceAdapter::getCountryCode, a -> a));
    }

    public ComplianceAdapter getAdapter(String countryCode) {
        ComplianceAdapter adapter = adapters.get(countryCode);
        if (adapter == null)
            throw new BadRequestException("No compliance adapter for country: " + countryCode);
        return adapter;
    }
}
```

---

## 7. Kafka Topics

### Consumed

| Topic | Consumer Group | Action |
|---|---|---|
| `twende.rides.completed` | `twende-compliance` | Create `trip_report` record from ride data |
| `twende.rides.cancelled` | `twende-compliance` | Write to `audit_log` |
| `twende.rides.requested` | `twende-compliance` | Write to `audit_log` |
| `twende.payments.completed` | `twende-compliance` | Write to `audit_log` |
| `twende.payments.failed` | `twende-compliance` | Write to `audit_log` |
| `twende.subscriptions.activated` | `twende-compliance` | Write to `audit_log` |
| `twende.subscriptions.expired` | `twende-compliance` | Write to `audit_log` |
| `twende.users.registered` | `twende-compliance` | Write to `audit_log` |
| `twende.drivers.approved` | `twende-compliance` | Write to `audit_log` |
| `twende.drivers.status-updated` | `twende-compliance` | Write to `audit_log` |
| `twende.ratings.submitted` | `twende-compliance` | Write to `audit_log` |

### Not Published

The compliance service does not publish Kafka events. It is a terminal consumer.

---

## 8. Batch Submission

The core submission logic runs as a scheduled job every hour:

```java
@Scheduled(cron = "0 0 * * * *")  // top of every hour
public void submitPendingReports() {
    for (Map.Entry<String, ComplianceAdapter> entry : adapters.entrySet()) {
        String countryCode = entry.getKey();
        ComplianceAdapter adapter = entry.getValue();

        if (!adapter.isTripReportingRequired()) continue;

        List<TripReport> pending = tripReportRepository
            .findByCountryCodeAndSubmittedFalseOrderByCreatedAtAsc(
                countryCode, PageRequest.of(0, 500));

        for (TripReport report : pending) {
            try {
                adapter.submitTripReport(report);
                report.setSubmitted(true);
                report.setSubmittedAt(Instant.now());
                report.setSubmissionError(null);  // clear previous error
            } catch (ComplianceException e) {
                report.setSubmissionError(e.getMessage());
                // Will be retried in next hourly batch
            }
            tripReportRepository.save(report);
        }

        log.info("Compliance batch for {}: {} pending, processed {}",
            countryCode, pending.size(), pending.size());
    }
}
```

**Key behaviors:**
- Processes up to 500 reports per country per batch run.
- On success: sets `submitted = true`, `submittedAt = now()`, stores `submissionRef`.
- On failure: stores the error message in `submissionError`, leaves `submitted = false`.
  The report will be retried in the next hourly batch.
- Previous errors are cleared on successful retry.

---

## 9. Trip Report Creation (from Kafka)

```java
@KafkaListener(topics = "twende.rides.completed", groupId = "${spring.application.name}")
public void onRideCompleted(RideCompletedEvent event) {
    // Check idempotency -- don't create duplicate reports
    if (tripReportRepository.existsByRideId(event.getRideId())) {
        log.warn("Trip report already exists for ride {}", event.getRideId());
        return;
    }

    TripReport report = new TripReport();
    report.setCountryCode(event.getCountryCode());
    report.setRideId(event.getRideId());
    report.setDriverId(event.getDriverId());
    report.setRiderId(event.getRiderId());
    report.setVehicleType(event.getVehicleType());
    report.setPickupLat(event.getPickupLat());
    report.setPickupLng(event.getPickupLng());
    report.setDropoffLat(event.getDropoffLat());
    report.setDropoffLng(event.getDropoffLng());
    report.setDistanceMetres(event.getDistanceMetres());
    report.setDurationSeconds(event.getDurationSeconds());
    report.setFare(event.getFinalFare());
    report.setCurrency(event.getCurrencyCode());
    report.setStartedAt(event.getStartedAt());
    report.setCompletedAt(event.getCompletedAt());
    tripReportRepository.save(report);

    // Also write to audit log
    auditService.log(event.getCountryCode(), "RIDE_COMPLETED",
        event.getRideId(), event.getDriverId(), event);
}
```

---

## 10. Audit Log Service

```java
@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(String countryCode, String eventType,
                    UUID entityId, UUID actorId, Object payload) {
        AuditLog entry = new AuditLog();
        entry.setCountryCode(countryCode);
        entry.setEventType(eventType);
        entry.setEntityId(entityId);
        entry.setActorId(actorId);
        entry.setPayload(objectMapper.writeValueAsString(payload));
        entry.setOccurredAt(Instant.now());
        auditLogRepository.save(entry);
    }
}
```

The audit log is append-only. Entries are never updated or deleted. All significant
Kafka events are written here, providing a complete audit trail for regulatory review.

---

## 11. Configuration

```yaml
server:
  port: 8094

spring:
  application:
    name: twende-compliance
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_compliance
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
  kafka:
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
    consumer:
      group-id: ${spring.application.name}
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

twende:
  compliance:
    sumatra:
      base-url: ${SUMATRA_API_URL:https://api.sumatra.go.tz}
      api-key: ${SUMATRA_API_KEY:}
      enabled: ${SUMATRA_ENABLED:false}
    ntsa:
      base-url: ${NTSA_API_URL:}
      api-key: ${NTSA_API_KEY:}
      enabled: ${NTSA_ENABLED:false}
    kcca:
      base-url: ${KCCA_API_URL:}
      api-key: ${KCCA_API_KEY:}
      enabled: ${KCCA_ENABLED:false}

logging:
  level:
    com.twende: DEBUG
    org.springframework.security: WARN
```

---

## 12. Key Implementation Rules

1. **Adapter pattern is mandatory** -- all regulatory API calls go through a
   `ComplianceAdapter` implementation. Never call a regulator API directly from
   the service layer.

2. **One adapter per country** -- each `ComplianceAdapter` returns a single country
   code from `getCountryCode()`. Adding a new country means adding a new adapter class.

3. **Batch size is 500** -- process up to 500 unsubmitted reports per country per
   hourly batch. This prevents overwhelming the regulatory API.

4. **Idempotent report creation** -- check `existsByRideId()` before creating a
   trip report to handle Kafka redelivery safely.

5. **Never delete trip reports or audit logs** -- these are regulatory records.
   Failed submissions are retried, not deleted.

6. **Submission errors are stored, not thrown** -- when a submission fails, store
   the error in `submissionError` and continue processing the remaining batch.

7. **All endpoints require ADMIN role** -- compliance data is sensitive. Check
   `X-User-Role == "ADMIN"` on all endpoints.

8. **BigDecimal for fare amounts** -- the `fare` field in `TripReport` uses
   `BigDecimal` in Java and `NUMERIC(12,2)` in the database.

9. **Audit log captures all significant events** -- every Kafka event consumed
   by this service is written to the `audit_log` table for regulatory audit trails.

10. **RestClient for SUMATRA API** -- use Spring `RestClient` directly. No SDK,
    no Feign, no Eureka.

11. **All responses use `ApiResponse<T>` wrapper** -- consistent with the
    platform-wide response format.

12. **Failed submissions are retried automatically** -- reports with
    `submitted = false` are picked up in the next hourly batch run.
    No manual intervention needed for transient failures.

13. **Kafka key format** -- consumer group is `twende-compliance`. Messages are
    keyed by `{countryCode}:{entityId}`.

---

## 13. Testing

### Unit Tests

- `ComplianceService` -- test adapter resolution by country code
- `BatchSubmissionService`:
  - Successful submission marks report as submitted with ref
  - Failed submission stores error and leaves submitted as false
  - Skips countries where `isTripReportingRequired()` is false
  - Processes at most 500 records per batch
- `AuditService` -- verify correct mapping of events to audit log entries
- `SumatraAdapter` -- test payload mapping to SUMATRA format

### Integration Tests

- Use Testcontainers for PostgreSQL and Kafka
- Test end-to-end: publish `RideCompletedEvent` -> verify `trip_report` row created
- Test idempotency: publish same event twice -> verify only one report exists
- Test batch submission with mocked SUMATRA API (WireMock):
  - Successful submission updates report
  - Failed submission stores error
  - Mixed success/failure in same batch
- Test audit log: publish various events -> verify audit log rows

### Test Naming

```java
@Test
void givenRideCompletedEvent_whenConsumed_thenTripReportCreated() { ... }

@Test
void givenDuplicateRideEvent_whenConsumed_thenNoSecondReportCreated() { ... }

@Test
void givenPendingReports_whenBatchRuns_thenSubmittedToSumatra() { ... }

@Test
void givenSumatraApiFailure_whenBatchRuns_thenErrorStoredAndReportNotMarkedSubmitted() { ... }

@Test
void givenKenyaCountryCode_whenBatchRuns_thenSkippedBecauseReportingNotRequired() { ... }
```

### Coverage

- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw -pl compliance-service verify`
- Excluded from coverage: DTOs, enums, config classes, stub adapters (NTSA, KCCA)
