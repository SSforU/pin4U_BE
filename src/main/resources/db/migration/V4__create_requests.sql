CREATE TABLE IF NOT EXISTS requests (
    id               BIGSERIAL PRIMARY KEY,
    slug             VARCHAR(16) NOT NULL UNIQUE,
    owner_user_id    BIGINT      NOT NULL DEFAULT 1,
    station_code     VARCHAR(20) NOT NULL,
    request_message  VARCHAR(80) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_requests_station
        FOREIGN KEY (station_code) REFERENCES stations(code)
);

CREATE INDEX IF NOT EXISTS idx_requests_owner_created
    ON requests(owner_user_id, created_at DESC);
