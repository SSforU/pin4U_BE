-- 0) 유효 태그 검증 함수
CREATE OR REPLACE FUNCTION is_valid_note_tags(p_tags jsonb)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT
        p_tags IS NULL
        OR (
            jsonb_typeof(p_tags) = 'array'
            AND jsonb_array_length(p_tags) <= 3
            AND (
                SELECT COALESCE(bool_and(elem IN (
                    '분위기 맛집','핫플','힐링 스팟','또간집','숨은 맛집','가성비 갑'
                )), TRUE)
                FROM jsonb_array_elements_text(p_tags) AS t(elem)
            )
        );
$$;

-- 1) stations
CREATE TABLE IF NOT EXISTS stations (
    code varchar(16) PRIMARY KEY,
    name varchar(100) NOT NULL,
    line varchar(50) NOT NULL,
    lat numeric(11,7) NOT NULL,
    lng numeric(11,7) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_stations_name ON stations (name);

-- 2) users
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    nickname        VARCHAR(50)  NOT NULL,
    preference_text VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_users_nickname ON users(nickname);

-- 3) places  (카카오 파리티: 문자열 유지)
CREATE TABLE IF NOT EXISTS places (
    external_id         VARCHAR(100) PRIMARY KEY,      -- "kakao:{id}" | "mock:{key}"
    id                  VARCHAR(50)  NOT NULL,         -- kakao 원본 id(문자열)
    place_name          VARCHAR(200) NOT NULL,
    category_group_code VARCHAR(10),
    category_group_name VARCHAR(50),
    category_name       VARCHAR(300),
    phone               VARCHAR(50),
    address_name        VARCHAR(300),
    road_address_name   VARCHAR(300),
    x                   VARCHAR(50)  NOT NULL,         -- 문자열로 보관
    y                   VARCHAR(50)  NOT NULL,         -- 문자열로 보관
    place_url           VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_places_kakao_id ON places(id);

-- 4) place_mock : places.external_id 기준 1:1
CREATE TABLE IF NOT EXISTS place_mock (
  external_id VARCHAR(100) PRIMARY KEY
    REFERENCES places(external_id) ON DELETE CASCADE,
  rating NUMERIC(2,1),
  rating_count INT,
  review_snippets JSONB,
  image_urls JSONB,
  opening_hours JSONB,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5) place_summaries : places.external_id 기준 1:1
CREATE TABLE IF NOT EXISTS place_summaries (
    external_id       VARCHAR(100) PRIMARY KEY REFERENCES places(external_id) ON DELETE CASCADE,
    summary_text      TEXT,
    place_name        VARCHAR(200),
    road_address_name VARCHAR(300),
    phone             VARCHAR(50),
    opening_hours     JSONB,
    evidence          JSONB,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6) requests
CREATE TABLE IF NOT EXISTS requests (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(16) NOT NULL UNIQUE,
    owner_user_id   BIGINT REFERENCES users(id),
    station_code    VARCHAR(20) REFERENCES stations(code),
    request_message TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_requests_station ON requests(station_code);

-- 7) request_place_aggregates
CREATE TABLE IF NOT EXISTS request_place_aggregates (
    id                      BIGSERIAL PRIMARY KEY,
    request_id              BIGINT NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    external_id             VARCHAR(100) NOT NULL REFERENCES places(external_id) ON DELETE CASCADE,
    recommended_count       INTEGER     NOT NULL DEFAULT 0,
    first_recommended_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_recommended_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rpa_request_place ON request_place_aggregates(request_id, external_id);
CREATE INDEX IF NOT EXISTS idx_rpa_request  ON request_place_aggregates(request_id);
CREATE INDEX IF NOT EXISTS idx_rpa_external ON request_place_aggregates(external_id);

-- 8) recommendation_notes
CREATE TABLE IF NOT EXISTS recommendation_notes (
    id                   BIGSERIAL PRIMARY KEY,
    rpa_id               BIGINT NOT NULL REFERENCES request_place_aggregates(id) ON DELETE CASCADE,
    nickname             VARCHAR(50),
    recommend_message    VARCHAR(300),
    image_url            VARCHAR(300),
    tags                 JSONB,
    guest_id             UUID    NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at_minute    TIMESTAMPTZ,
    CONSTRAINT chk_notes_tags_valid CHECK (is_valid_note_tags(tags))
);
CREATE INDEX IF NOT EXISTS idx_notes_rpa ON recommendation_notes(rpa_id);
CREATE INDEX IF NOT EXISTS idx_notes_guest_id_created_at ON recommendation_notes(guest_id, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_notes_agg_guest_minute
ON recommendation_notes (rpa_id, guest_id, created_at_minute);

-- created_at_minute 유지 트리거
CREATE OR REPLACE FUNCTION recommendation_notes_set_minute()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.created_at_minute := date_trunc('minute', NEW.created_at);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_recommendation_notes_set_minute ON recommendation_notes;
CREATE TRIGGER trg_recommendation_notes_set_minute
BEFORE INSERT OR UPDATE OF created_at ON recommendation_notes
FOR EACH ROW
EXECUTE FUNCTION recommendation_notes_set_minute();
