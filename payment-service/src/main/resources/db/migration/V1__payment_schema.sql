-- =============================================================================
-- V1__payment_schema.sql
-- Payment service schema: transactions, driver_wallets, wallet_entries,
--                         cash_declarations
-- =============================================================================

CREATE TABLE transactions (
    id              UUID          PRIMARY KEY,
    country_code    CHAR(2)       NOT NULL,
    reference_id    UUID          NOT NULL,
    reference_type  VARCHAR(20)   NOT NULL,
    payer_id        UUID          NOT NULL,
    payee_id        UUID,
    payment_type    VARCHAR(20)   NOT NULL,
    payment_method  VARCHAR(30)   NOT NULL,
    provider        VARCHAR(30),
    amount          NUMERIC(12,2) NOT NULL,
    currency_code   VARCHAR(3)    NOT NULL,
    status          VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    provider_ref    VARCHAR(200),
    failure_reason  TEXT,
    initiated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,

    CONSTRAINT uq_transactions_reference UNIQUE (reference_id, reference_type)
);

CREATE INDEX idx_transactions_payer     ON transactions(payer_id);
CREATE INDEX idx_transactions_reference ON transactions(reference_id);
CREATE INDEX idx_transactions_status    ON transactions(status);

-- -----------------------------------------------------------------------------
-- driver_wallets — one wallet per driver, driver_id is the PK (not generated)
-- -----------------------------------------------------------------------------
CREATE TABLE driver_wallets (
    driver_id    UUID          PRIMARY KEY,
    country_code CHAR(2)       NOT NULL,
    balance      NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency     VARCHAR(3)    NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- wallet_entries — immutable ledger of all wallet credits and debits
-- -----------------------------------------------------------------------------
CREATE TABLE wallet_entries (
    id            UUID          PRIMARY KEY,
    driver_id     UUID          NOT NULL REFERENCES driver_wallets(driver_id),
    type          VARCHAR(20)   NOT NULL,
    amount        NUMERIC(12,2) NOT NULL,
    balance_after NUMERIC(12,2) NOT NULL,
    reference_id  UUID,
    description   TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_wallet_entries_driver ON wallet_entries(driver_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- cash_declarations — driver declares cash received for a ride
-- -----------------------------------------------------------------------------
CREATE TABLE cash_declarations (
    id          UUID          PRIMARY KEY,
    ride_id     UUID          NOT NULL UNIQUE,
    driver_id   UUID          NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    declared_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    verified    BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_cash_declarations_driver ON cash_declarations(driver_id);
