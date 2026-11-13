-- Write model: owned by the claim module.

CREATE SEQUENCE claim_number_seq START 1;

CREATE TABLE claims (
    id                   UUID PRIMARY KEY,
    claim_number         VARCHAR(32)    NOT NULL UNIQUE,
    claimant_name        VARCHAR(100)   NOT NULL,
    policy_number        VARCHAR(32)    NOT NULL,
    claim_type           VARCHAR(16)    NOT NULL,
    market               VARCHAR(2)     NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    incident_date        DATE           NOT NULL,
    incident_description VARCHAR(2000)  NOT NULL,
    claimed_amount       NUMERIC(15, 2) NOT NULL CHECK (claimed_amount > 0),
    settled_amount       NUMERIC(15, 2),
    decision_note        VARCHAR(1000),
    status               VARCHAR(16)    NOT NULL,
    assigned_handler     VARCHAR(100),
    submitted_at         TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    decided_at           TIMESTAMPTZ,
    version              BIGINT         NOT NULL
);

CREATE INDEX idx_claims_status ON claims (status);
CREATE INDEX idx_claims_claimant ON claims (claimant_name);
CREATE INDEX idx_claims_handler ON claims (assigned_handler);

CREATE TABLE info_requests (
    id           UUID PRIMARY KEY,
    claim_id     UUID          NOT NULL REFERENCES claims (id),
    question     VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(100)  NOT NULL,
    requested_at TIMESTAMPTZ   NOT NULL,
    answer       VARCHAR(2000),
    answered_at  TIMESTAMPTZ
);

CREATE INDEX idx_info_requests_claim ON info_requests (claim_id);

-- Read models: owned by the exposure module and written only by the Kafka consumer.

CREATE TABLE liability_exposures (
    market             VARCHAR(2)     NOT NULL,
    claim_type         VARCHAR(16)    NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    open_claims        INTEGER        NOT NULL,
    outstanding_amount NUMERIC(17, 2) NOT NULL,
    settled_amount     NUMERIC(17, 2) NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    PRIMARY KEY (market, claim_type, currency)
);

CREATE TABLE claim_audit_log (
    event_id        UUID PRIMARY KEY,
    claim_id        UUID        NOT NULL,
    claim_number    VARCHAR(32) NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    previous_status VARCHAR(16),
    new_status      VARCHAR(16) NOT NULL,
    actor           VARCHAR(100) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_claim ON claim_audit_log (claim_id, occurred_at);

-- Idempotency ledger: one row per event the consumer has applied.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
