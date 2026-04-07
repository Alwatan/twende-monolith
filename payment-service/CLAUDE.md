# CLAUDE.md — Payment Service

> Payment abstraction layer for the Twende ride-hailing platform.
> Read this fully before writing any code in this module.
> Also read the root `CLAUDE.md` for platform-wide conventions.

---

## 1. What This Service Does

The payment service sits between business services (ride, subscription) and external payment
providers (Selcom Tanzania, cash). It manages driver wallets, processes subscription payments
via Selcom mobile money, handles driver payouts, records cash declarations, and credits
driver wallets for free loyalty rides. A provider-agnostic internal API means adding Kenya's
M-Pesa or Uganda's MTN Mobile Money requires adding an adapter, not changing the ride or
subscription services.

**Port:** 8089
**Database:** `twende_payments`

---

## 2. API Endpoints

### Internal (service-to-service only)

| Method | Path | Description |
|---|---|---|
| `POST` | `/internal/payments/ride` | Initiate ride payment |
| `POST` | `/internal/payments/subscription` | Initiate subscription payment |
| `POST` | `/internal/payments/refund` | Issue refund |
| `GET` | `/internal/payments/{id}` | Get payment status |

### Driver-facing (authenticated)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/payments/wallet` | Driver wallet balance and history |
| `GET` | `/api/v1/payments/earnings` | Earnings summary (daily/weekly/monthly) |
| `POST` | `/api/v1/payments/withdraw` | Withdraw from wallet to mobile money |
| `POST` | `/api/v1/payments/{rideId}/cash-declare` | Declare cash received for a ride |

### Rider-facing (authenticated)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/payments/history` | Rider payment history |
| `GET` | `/api/v1/payments/{rideId}` | Get payment for a specific ride |

---

## 3. Database Schema

Migration file: `V1__create_payment_schema.sql`

```sql
CREATE TYPE payment_status AS ENUM ('PENDING','PROCESSING','COMPLETED','FAILED','REFUNDED');
CREATE TYPE payment_type   AS ENUM ('RIDE','SUBSCRIPTION','WITHDRAWAL','REFUND');

CREATE TABLE transactions (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code    CHAR(2)       NOT NULL,
    reference_id    UUID          NOT NULL,    -- rideId or subscriptionId
    reference_type  VARCHAR(20)   NOT NULL,    -- 'RIDE' | 'SUBSCRIPTION'
    payer_id        UUID          NOT NULL,    -- riderId or driverId
    payee_id        UUID,                      -- driverId for rides, null for subscriptions
    payment_type    payment_type  NOT NULL,
    payment_method  VARCHAR(30)   NOT NULL,    -- MOBILE_MONEY | CASH | CARD
    provider        VARCHAR(30),               -- 'selcom' | 'cash'
    amount          NUMERIC(12,2) NOT NULL,
    currency_code   VARCHAR(3)    NOT NULL,
    status          payment_status NOT NULL DEFAULT 'PENDING',
    provider_ref    VARCHAR(200),              -- Selcom transaction ID
    failure_reason  TEXT,
    initiated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    UNIQUE(reference_id, reference_type)
);

CREATE TABLE driver_wallets (
    driver_id    UUID          PRIMARY KEY,
    country_code CHAR(2)       NOT NULL,
    balance      NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency     VARCHAR(3)    NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE wallet_entries (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id    UUID          NOT NULL REFERENCES driver_wallets(driver_id),
    type         VARCHAR(20)   NOT NULL,  -- CREDIT (ride earnings) | DEBIT (withdrawal, subscription)
    amount       NUMERIC(12,2) NOT NULL,
    balance_after NUMERIC(12,2) NOT NULL,
    reference_id UUID,
    description  TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE cash_declarations (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id      UUID          NOT NULL UNIQUE,
    driver_id    UUID          NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    declared_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    verified     BOOLEAN       NOT NULL DEFAULT false
);

CREATE INDEX idx_transactions_payer ON transactions(payer_id);
CREATE INDEX idx_transactions_reference ON transactions(reference_id);
CREATE INDEX idx_wallet_entries_driver ON wallet_entries(driver_id, created_at DESC);
```

---

## 4. Payment Flows

### Cash Payment (most common in Tanzania)

Riders pay cash directly to the driver at the end of the trip. No digital processing needed
on the rider side.

```
1. Driver presses "Collect Cash" in app after ride completion
2. Driver app calls POST /api/v1/payments/{rideId}/cash-declare
3. Payment service creates cash_declaration record
4. Credits driver wallet (system trusts driver declaration; reconciliation is manual)
5. Transaction recorded as COMPLETED, method=CASH
```

### Free Loyalty Ride (Twende pays the driver)

When a ride marked as `freeRide = true` completes, Twende credits the driver's wallet with
the full calculated fare. The driver is never penalised for accepting a free ride.

```
1. ride-service publishes RideCompletedEvent with freeRide=true
2. payment-service consumes twende.rides.completed
3. Creates transaction record (payer=SYSTEM, status=COMPLETED)
4. Credits driver wallet with ride.finalFare via wallet_entries
5. Publishes PaymentCompletedEvent
```

### Subscription Payment (Selcom mobile money)

```
1. subscription-service calls POST /internal/payments/subscription
2. payment-service creates transaction record (status=PENDING)
3. Calls Selcom push-pay API to debit driver's mobile money
4. On success: status -> COMPLETED, store providerRef
5. On failure: status -> FAILED, store failureReason
6. Returns result to subscription-service
```

### Driver Wallet Withdrawal (Selcom disburse)

```
1. Driver calls POST /api/v1/payments/withdraw { amount, mobileNumber }
2. Validate balance >= amount
3. Create transaction record (status=PROCESSING)
4. Debit wallet (within @Transactional)
5. Call Selcom disburse API
6. On success: status -> COMPLETED
7. On failure: re-credit wallet, status -> FAILED
```

---

## 5. Provider Abstraction

```java
public interface PaymentProvider {
    String getId();   // "selcom", "cash", "mtn"
    PaymentResult charge(ChargeRequest request);
    PaymentResult disburse(DisburseRequest request);  // wallet payout
    RefundResult refund(RefundRequest request);
}

@Component
public class PaymentGateway {
    private final Map<String, PaymentProvider> providers;

    public PaymentResult charge(String providerId, ChargeRequest request) {
        return providers.get(providerId).charge(request);
    }
}
```

### SelcomProvider

Uses Spring `RestClient` to call the Selcom REST API directly. No SDK.

```java
@Component
public class SelcomProvider implements PaymentProvider {
    private final RestClient restClient;

    public String getId() { return "selcom"; }

    // push-pay for driver subscription purchase
    // disburse for driver wallet payouts
    // Always store providerRef from response for reconciliation
}
```

Adding new providers (e.g., `SafaricomProvider` for Kenya M-Pesa, `MtnProvider` for Uganda)
requires only a new `@Component` implementing `PaymentProvider`. No changes to the payment
service logic or to upstream services (ride, subscription).

---

## 6. Wallet Management

### Transactional Wallet Updates (CRITICAL)

Wallet balance updates and wallet_entry inserts MUST happen in a single `@Transactional`
method. Never update the balance without creating a corresponding entry.

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

@Transactional
public void debitDriverWallet(UUID driverId, BigDecimal amount, String description) {
    DriverWallet wallet = walletRepository.findByDriverId(driverId)
        .orElseThrow(() -> new BadRequestException("Wallet not found"));
    if (wallet.getBalance().compareTo(amount) < 0)
        throw new BadRequestException("Insufficient balance");
    wallet.setBalance(wallet.getBalance().subtract(amount));
    BigDecimal newBalance = wallet.getBalance();
    walletRepository.save(wallet);
    walletEntryRepository.save(new WalletEntry(driverId, "DEBIT", amount, newBalance, description));
}
```

---

## 7. Resilience and Retry

### Circuit Breaker (Resilience4j)

Selcom API calls are wrapped in a circuit breaker. On provider failure, the transaction is
left in `PROCESSING` status and retried by the scheduled job.

### Failed Transaction Retry

```java
@Scheduled(fixedDelay = 300_000)  // every 5 minutes
public void retryFailedTransactions() {
    List<Transaction> processing = transactionRepository
        .findByStatusAndInitiatedAtBefore(PaymentStatus.PROCESSING,
            Instant.now().minus(5, ChronoUnit.MINUTES));
    for (Transaction tx : processing) {
        try {
            PaymentResult result = paymentGateway.charge(tx.getProvider(), toChargeRequest(tx));
            tx.setStatus(PaymentStatus.COMPLETED);
            tx.setProviderRef(result.getReference());
            tx.setCompletedAt(Instant.now());
        } catch (Exception e) {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setFailureReason(e.getMessage());
        }
        transactionRepository.save(tx);
    }
}
```

---

## 8. Kafka Integration

### Consumed Topics

| Topic | Event | Action |
|---|---|---|
| `twende.rides.completed` | `RideCompletedEvent` | If `freeRide=true`: credit driver wallet with calculated fare (Twende pays). Cash rides: driver already has cash, create transaction record only. |
| `twende.subscriptions.activated` | `SubscriptionActivatedEvent` | Record subscription charge transaction |

### Published Topics

| Topic | Event | Trigger |
|---|---|---|
| `twende.payments.completed` | `PaymentCompletedEvent` | Successful payment processing |
| `twende.payments.failed` | `PaymentFailedEvent` | Failed payment processing |

---

## 9. Inter-Service Communication

**No Eureka. No Feign. Use Spring `RestClient` for all inter-service calls.**

This service is primarily called BY other services (subscription-service calls the internal
API). It does not depend heavily on other services, but may call:

| Service | What We Get | How |
|---|---|---|
| **country-config-service** | Currency config, payment method config | RestClient, cached in Redis |

---

## 10. Configuration

```yaml
server:
  port: 8089

spring:
  application:
    name: twende-payment
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_payments
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: payment-service

twende:
  selcom:
    api-key: ${SELCOM_API_KEY:}
    api-secret: ${SELCOM_API_SECRET:}
    base-url: ${SELCOM_BASE_URL:https://apigw.selcom.net/v1}
  services:
    country-config:
      base-url: ${COUNTRY_CONFIG_URL:http://localhost:8082}
```

---

## 11. Key Business Rules

1. **Driver keeps 100% of ride fare** -- Twende earns from subscription bundles only.
   Never deduct a percentage from the ride payment.

2. **Riders pay cash only** -- all rider payments are physical cash at end of trip.
   No digital payment processing on the rider side. Selcom is only for driver
   subscriptions and wallet payouts.

3. **Free rides are Twende's cost** -- when a loyalty free ride completes, Twende
   credits the driver's wallet with the full calculated fare. The driver is never
   penalised for accepting a free ride.

4. **Wallet updates are always transactional** -- balance update and wallet_entry insert
   in one `@Transactional` method. Never update the balance without an entry.

5. **Money arithmetic uses BigDecimal only** -- never `double`, never `float`.
   DB columns for money: `NUMERIC(12,2)`.

6. **Always store `providerRef`** from Selcom responses for reconciliation.

7. **Cash declarations are trust-based** -- the system trusts the driver's declaration.
   Reconciliation is manual/administrative.

8. **All responses use `ApiResponse<T>` wrapper** -- consistent with the platform-wide
   response format.

---

## 12. Testing

### Unit Tests

- `WalletService.creditDriverWallet(...)` -- balance and entry created atomically
- `WalletService.debitDriverWallet(...)` -- insufficient balance rejection
- `PaymentGateway` -- correct provider resolution
- `CashDeclarationService` -- validates ride exists, prevents duplicate declarations
- Free ride wallet credit -- verifies full fare credited on free ride completion

### Integration Tests

- Use Testcontainers for PostgreSQL, Redis, and Kafka
- Mock Selcom API responses with WireMock or MockRestServiceServer
- Test end-to-end: Kafka event consumed -> transaction created -> wallet credited
- Test withdrawal flow: request -> wallet debit -> Selcom disburse -> transaction completed
- Test retry scheduler: PROCESSING transactions retried after 5 minutes

### Test Naming

```java
@Test
void givenCompletedFreeRide_whenRideCompletedEventConsumed_thenDriverWalletCredited() { ... }

@Test
void givenCashRide_whenDriverDeclaresCash_thenWalletCreditedAndTransactionRecorded() { ... }

@Test
void givenInsufficientBalance_whenWithdrawRequested_thenBadRequestReturned() { ... }

@Test
void givenSelcomFailure_whenSubscriptionPayment_thenTransactionMarkedProcessingAndRetried() { ... }
```

### Coverage

- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw verify`
- Excluded from coverage: DTOs, enums, config classes
