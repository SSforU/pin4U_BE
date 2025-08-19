-- 0) 유효 태그 검증 함수 (이미 제공한 내용과 동일)
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
    code        VARCHAR(20) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    line        VARCHAR(30)  NOT NULL,
    lat         NUMERIC(10,7),
    lng         NUMERIC(10,7)
);
CREATE INDEX IF NOT EXISTS idx_stations_name ON stations(name);
CREATE INDEX IF NOT EXISTS idx_stations_line ON stations(line);

-- 2) users
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    nickname        VARCHAR(50)  NOT NULL,
    preference_text VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_users_nickname ON users(nickname);

-- 3) places (카카오 파리티)
CREATE TABLE IF NOT EXISTS places (
    external_id         VARCHAR(100) PRIMARY KEY,
    id                  VARCHAR(50)  NOT NULL,
    place_name          VARCHAR(200) NOT NULL,
    category_group_code VARCHAR(10),
    category_group_name VARCHAR(50),
    category_name       VARCHAR(300),
    phone               VARCHAR(50),
    address_name        VARCHAR(300),
    road_address_name   VARCHAR(300),
    x                   VARCHAR(50)  NOT NULL,
    y                   VARCHAR(50)  NOT NULL,
    place_url           VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_places_kakao_id ON places(id);

-- 4) place_mock
CREATE TABLE place_mock (
  place_id VARCHAR(64) PRIMARY KEY
    REFERENCES places(id) ON DELETE CASCADE,
  rating NUMERIC(2,1),
  rating_count INT,
  review_snippets JSONB,
  image_urls JSONB,
  opening_hours VARCHAR(300),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5) place_summaries
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
    id          BIGSERIAL PRIMARY KEY,
    rpa_id      BIGINT NOT NULL REFERENCES request_place_aggregates(id) ON DELETE CASCADE,
    nickname    VARCHAR(50),
    memo        VARCHAR(300),
    image_url   VARCHAR(300),
    tags        JSONB,
    guest_id    UUID    NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 🔁 생성열 대신 일반 컬럼으로 두고 트리거로 유지
    created_at_minute TIMESTAMPTZ,

    CONSTRAINT chk_notes_tags_valid CHECK (is_valid_note_tags(tags))
);

CREATE INDEX IF NOT EXISTS idx_notes_rpa ON recommendation_notes(rpa_id);
CREATE INDEX IF NOT EXISTS idx_notes_guest_id_created_at ON recommendation_notes(guest_id, created_at);

-- ✅ 함수 없는 유니크 인덱스(중복 제출 방지: 같은 rpa, 같은 guest, 같은 분)
CREATE UNIQUE INDEX IF NOT EXISTS uq_notes_agg_guest_minute
ON recommendation_notes (rpa_id, guest_id, created_at_minute);

-- 🔧 created_at_minute 유지 트리거
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
