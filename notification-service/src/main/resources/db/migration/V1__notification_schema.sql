-- V1__notification_schema.sql
-- Notification service schema: templates, FCM tokens, and notification log

CREATE TABLE notification_templates (
    id           UUID        PRIMARY KEY,
    template_key VARCHAR(100) NOT NULL,
    locale       VARCHAR(10)  NOT NULL,
    channel      VARCHAR(10)  NOT NULL,
    subject      VARCHAR(200),
    body         TEXT         NOT NULL,
    country_code CHAR(2)      NOT NULL DEFAULT 'TZ',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(template_key, locale, channel)
);

CREATE TABLE fcm_tokens (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL,
    token        TEXT        NOT NULL,
    platform     VARCHAR(10) NOT NULL,
    is_active    BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, token)
);

CREATE TABLE notification_log (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL,
    country_code CHAR(2)     NOT NULL,
    channel      VARCHAR(10) NOT NULL,
    template_key VARCHAR(100),
    title        VARCHAR(200),
    body         TEXT,
    status       VARCHAR(20) NOT NULL,
    provider     VARCHAR(30),
    provider_ref VARCHAR(200),
    error        TEXT,
    sent_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fcm_user ON fcm_tokens(user_id) WHERE is_active = true;
CREATE INDEX idx_notif_log_user ON notification_log(user_id, sent_at DESC);
