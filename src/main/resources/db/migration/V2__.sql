-- V2: align place_mock / place_summaries with entities and provide view

-- ===== place_mock =====
ALTER TABLE place_mock DROP CONSTRAINT IF EXISTS place_mock_pkey;
ALTER TABLE place_mock DROP CONSTRAINT IF EXISTS fk_place_mock_place;

-- id 쓰던 구조였다면 드랍(비어있는 스키마 전제라 안전). 이미 없으면 무시됨.
ALTER TABLE place_mock DROP COLUMN IF EXISTS id;

-- place_id (PK & FK to places)
ALTER TABLE place_mock
  ADD COLUMN IF NOT EXISTS place_id BIGINT;

ALTER TABLE place_mock
  ADD CONSTRAINT place_mock_pkey PRIMARY KEY (place_id);

ALTER TABLE place_mock
  ADD CONSTRAINT fk_place_mock_place
  FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE;

-- 확장 컬럼들(없으면 추가)
ALTER TABLE place_mock
  ADD COLUMN IF NOT EXISTS rating NUMERIC(2,1);
ALTER TABLE place_mock
  ADD COLUMN IF NOT EXISTS rating_count INT;
ALTER TABLE place_mock
  ADD COLUMN IF NOT EXISTS review_snippets JSONB;
ALTER TABLE place_mock
  ADD COLUMN IF NOT EXISTS image_urls JSONB;
ALTER TABLE place_mock
  ADD COLUMN IF NOT EXISTS opening_hours VARCHAR(300);
ALTER TABLE place_mock
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- ===== place_summaries =====
ALTER TABLE place_summaries DROP CONSTRAINT IF EXISTS place_summaries_pkey;
ALTER TABLE place_summaries DROP CONSTRAINT IF EXISTS fk_place_summaries_place;

-- 혹시 남아있을 수 있는 id, address_name 같은 과거 컬럼 제거(없으면 무시)
ALTER TABLE place_summaries DROP COLUMN IF EXISTS id;
ALTER TABLE place_summaries DROP COLUMN IF EXISTS address_name;

-- place_id (PK & FK to places)
ALTER TABLE place_summaries
  ADD COLUMN IF NOT EXISTS place_id BIGINT;

ALTER TABLE place_summaries
  ADD CONSTRAINT place_summaries_pkey PRIMARY KEY (place_id);

ALTER TABLE place_summaries
  ADD CONSTRAINT fk_place_summaries_place
  FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE;

-- 요약 전용 컬럼들
ALTER TABLE place_summaries
  ADD COLUMN IF NOT EXISTS summary_text VARCHAR(500);
ALTER TABLE place_summaries
  ADD COLUMN IF NOT EXISTS evidence VARCHAR(200);
ALTER TABLE place_summaries
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- ===== 통합 뷰 (기본정보 + mock + 요약) =====
CREATE OR REPLACE VIEW v_place_full AS
SELECT
  p.id AS place_id,
  p.name,
  p.category_name,
  p.address,
  p.phone,
  p.lat, p.lng,
  p.opening_hours AS opening_hours_kakao,
  pm.rating,
  pm.rating_count,
  pm.review_snippets,
  pm.image_urls,
  pm.opening_hours AS opening_hours_mock,
  ps.summary_text,
  ps.evidence,
  ps.updated_at AS summary_updated_at
FROM places p
LEFT JOIN place_mock pm ON pm.place_id = p.id
LEFT JOIN place_summaries ps ON ps.place_id = p.id;
