-- Social login support: Google and Apple ID token authentication

-- Add social login columns to auth_users
ALTER TABLE auth_users ADD COLUMN auth_provider VARCHAR(10) NOT NULL DEFAULT 'PHONE';
ALTER TABLE auth_users ADD COLUMN email VARCHAR(255);
ALTER TABLE auth_users ADD COLUMN profile_photo_url VARCHAR(500);
ALTER TABLE auth_users ADD COLUMN full_name VARCHAR(255);

-- Make phone_number nullable for social-only users
ALTER TABLE auth_users ALTER COLUMN phone_number DROP NOT NULL;

-- Add unique index on email (partial — only non-null values)
CREATE UNIQUE INDEX idx_auth_users_email ON auth_users(email) WHERE email IS NOT NULL;

-- Auth user links: multiple auth methods per account
CREATE TABLE auth_user_links (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES auth_users(id),
    provider          VARCHAR(10)  NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    email             VARCHAR(255),
    country_code      CHAR(2)      NOT NULL DEFAULT 'TZ',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_auth_user_links_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_auth_user_links_user_id ON auth_user_links(user_id);
CREATE INDEX idx_auth_user_links_provider ON auth_user_links(provider, provider_user_id);
