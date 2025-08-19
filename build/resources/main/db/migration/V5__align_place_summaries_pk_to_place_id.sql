-- V5: place_summaries 키를 external_id → place_id로 표준화
-- 규칙(최상위 사실): place_summaries(place_id PK, FK→places.id)
-- 본 스크립트는 현재 테이블 상태가
--  1) 이미 place_id 체계이거나
--  2) external_id PK 체계이거나
-- 어떤 경우든 "여러 번 실행해도 안전(idempotent)"하게 동작합니다.

-- 1) 필요한 컬럼 추가
ALTER TABLE IF EXISTS place_summaries
  ADD COLUMN IF NOT EXISTS place_id VARCHAR(64);

-- 2) 기존 external_id 기반 환경에서 place_id 채움
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='place_summaries' AND column_name='external_id'
  ) THEN
    -- external_id → places.id 매핑으로 place_id 보정
    UPDATE place_summaries ps
       SET place_id = p.id
      FROM places p
     WHERE ps.place_id IS NULL
       AND ps.external_id = p.external_id;
  END IF;
END $$;

-- 3) NOT NULL 및 FK 보장
DO $$
BEGIN
  -- place_id NOT NULL 제약
  BEGIN
    ALTER TABLE place_summaries
      ALTER COLUMN place_id SET NOT NULL;
  EXCEPTION WHEN others THEN
    -- (이미 NOT NULL이거나 데이터 미충족 시 다음 단계에서 실패하므로 그대로 둠)
  END;

  -- FK 없으면 추가
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
     WHERE table_schema='public' AND table_name='place_summaries' AND constraint_name='fk_place_summaries_place'
  ) THEN
    ALTER TABLE place_summaries
      ADD CONSTRAINT fk_place_summaries_place
      FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE;
  END IF;
END $$;

-- 4) PK를 place_id로 전환
DO $$
DECLARE
  pk_name TEXT;
BEGIN
  SELECT tc.constraint_name INTO pk_name
    FROM information_schema.table_constraints tc
   WHERE tc.table_schema='public' AND tc.table_name='place_summaries' AND tc.constraint_type='PRIMARY KEY'
   LIMIT 1;

  IF pk_name IS NOT NULL THEN
    -- 기존 PK가 external_id 기반일 수 있으므로 일단 드롭 후 재생성
    EXECUTE format('ALTER TABLE place_summaries DROP CONSTRAINT %I', pk_name);
  END IF;

  -- 원하는 PK로 재생성(중복 실행 안전)
  BEGIN
    ALTER TABLE place_summaries ADD CONSTRAINT pk_place_summaries PRIMARY KEY (place_id);
  EXCEPTION WHEN duplicate_object THEN
    -- 이미 동일 PK가 존재할 경우 무시
  END;
END $$;

-- 5) 정답 스키마의 보조/기본 정보 컬럼 보장
ALTER TABLE IF EXISTS place_summaries
  ADD COLUMN IF NOT EXISTS summary_text      TEXT,
  ADD COLUMN IF NOT EXISTS evidence          JSONB,
  ADD COLUMN IF NOT EXISTS place_name        VARCHAR(200),
  ADD COLUMN IF NOT EXISTS road_address_name VARCHAR(300),
  ADD COLUMN IF NOT EXISTS phone             VARCHAR(50),
  ADD COLUMN IF NOT EXISTS opening_hours     JSONB,
  ADD COLUMN IF NOT EXISTS updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 6) 과거 external_id 컬럼/인덱스 제거(있으면)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='place_summaries' AND column_name='external_id'
  ) THEN
    ALTER TABLE place_summaries DROP COLUMN external_id;
  END IF;
END $$;

-- 7) 성능 보조 인덱스
CREATE INDEX IF NOT EXISTS idx_place_summaries_updated_at ON place_summaries(updated_at);
CREATE INDEX IF NOT EXISTS idx_place_summaries_place_id   ON place_summaries(place_id);
