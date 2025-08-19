-- V2__fix_place_fk_to_external_id.sql

-- place_mock
ALTER TABLE IF EXISTS place_mock
  DROP CONSTRAINT IF EXISTS place_mock_pkey,
  DROP CONSTRAINT IF EXISTS fk_place_mock_place;

-- place_id 이름 바뀐 적 있으면 일단 통일
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'place_mock' AND column_name = 'place_id'
  ) THEN
    ALTER TABLE place_mock RENAME COLUMN place_id TO external_id;
  END IF;
END$$;

-- 타입을 varchar(100)으로 강제 (기존에 varchar면 그대로 통과)
ALTER TABLE place_mock
  ALTER COLUMN external_id TYPE varchar(100) USING external_id::varchar;

-- PK & FK 재설정
ALTER TABLE place_mock
  ADD CONSTRAINT place_mock_pkey PRIMARY KEY (external_id);

ALTER TABLE place_mock
  ADD CONSTRAINT fk_place_mock_place
  FOREIGN KEY (external_id) REFERENCES places(external_id) ON DELETE CASCADE;

-- 존재하지 않을 수 있는 컬럼들 보강(있으면 무시)
ALTER TABLE place_mock
  ADD COLUMN IF NOT EXISTS rating NUMERIC(2,1),
  ADD COLUMN IF NOT EXISTS rating_count INT,
  ADD COLUMN IF NOT EXISTS review_snippets JSONB,
  ADD COLUMN IF NOT EXISTS image_urls JSONB,
  ADD COLUMN IF NOT EXISTS opening_hours VARCHAR(300),
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();


-- place_summaries
ALTER TABLE IF EXISTS place_summaries
  DROP CONSTRAINT IF EXISTS place_summaries_pkey,
  DROP CONSTRAINT IF EXISTS fk_place_summaries_place;

-- 과거 컬럼 정리
ALTER TABLE place_summaries
  DROP COLUMN IF EXISTS id,
  DROP COLUMN IF EXISTS address_name;

-- external_id 존재/타입 보정
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'place_summaries' AND column_name = 'external_id'
  ) THEN
    ALTER TABLE place_summaries ADD COLUMN external_id varchar(100);
  END IF;
END$$;

ALTER TABLE place_summaries
  ALTER COLUMN external_id TYPE varchar(100) USING external_id::varchar;

-- PK & FK 재설정
ALTER TABLE place_summaries
  ADD CONSTRAINT place_summaries_pkey PRIMARY KEY (external_id);

ALTER TABLE place_summaries
  ADD CONSTRAINT fk_place_summaries_place
  FOREIGN KEY (external_id) REFERENCES places(external_id) ON DELETE CASCADE;

-- 요약 컬럼(있으면 통과)
ALTER TABLE place_summaries
  ADD COLUMN IF NOT EXISTS summary_text TEXT,
  ADD COLUMN IF NOT EXISTS evidence JSONB,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();


-- 뷰(외래키를 external_id로 조인)
CREATE OR REPLACE VIEW v_place_full AS
SELECT
  p.external_id,
  p.id AS kakao_id,
  p.place_name AS name,
  p.category_name,
  p.road_address_name AS address,
  p.phone,
  p.x, p.y,               -- 문자열 그대로 (카카오 원형)
  pm.rating,
  pm.rating_count,
  pm.review_snippets,
  pm.image_urls,
  pm.opening_hours AS opening_hours_mock,
  ps.summary_text,
  ps.evidence,
  ps.updated_at AS summary_updated_at
FROM places p
LEFT JOIN place_mock pm       ON pm.external_id = p.external_id
LEFT JOIN place_summaries ps  ON ps.external_id = p.external_id;
