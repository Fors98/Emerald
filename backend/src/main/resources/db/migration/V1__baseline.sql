-- Idempotent baseline for a fresh Emerald PostgreSQL database.
-- Does not drop or truncate any existing table. Review against live schema before applying.
CREATE TABLE public.contacts (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    company VARCHAR(255),
    project_type VARCHAR(255),
    message VARCHAR(5000) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE public.site_content (
    content_key VARCHAR(120) PRIMARY KEY,
    content_value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
