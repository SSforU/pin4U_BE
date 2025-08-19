-- ================================
-- V2: mock/summary 보강 (SSOT 일치)
--  - place_summaries.external_id  → places.external_id (VARCHAR) FK
--  - place_mock.place_id          → places.id (VARCHAR) FK
--  - 이미 존재하는 경우 안전하게 스킵(idempotent)
-- ================================

-- 1) place_summaries 테이블: SSOT 정의대로 생성 (없을 때만)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.tables
         WHERE table_schema = 'public'
           AND table_name   = 'place_summaries'
    ) THEN
        CREATE TABLE place_summaries (
            external_id       VARCHAR(100) PRIMARY KEY
                REFERENCES places(external_id) ON DELETE CASCADE,
            summary_text      TEXT,
            place_name        VARCHAR(200),
            road_address_name VARCHAR(300),
            phone             VARCHAR(50),
            opening_hours     JSONB,
            evidence          JSONB,
            updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
    END IF;
END $$;

-- 1-1) 만약 과거 스키마에 bigint place_id 등이 있다면 정리(보호적 처리)
-- (이 절은 기존 잘못된 컬럼을 가진 환경만을 위한 가드입니다. 없으면 자동 스킵)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name   = 'place_summaries'
           AND column_name  = 'place_id'
    ) THEN
        -- 잘못된 FK/컬럼 제거 (존재 시)
        BEGIN
            ALTER TABLE place_summaries DROP CONSTRAINT IF EXISTS fk_place_summaries_place;
        EXCEPTION WHEN undefined_object THEN
            -- no-op
        END;
        ALTER TABLE place_summaries DROP COLUMN IF EXISTS place_id;
    END IF;
END $$;
-----------------------------------------------------------------------------
-- 2)-- ===== place_mock: place_id BIGINT + FK(places.id) 일치 =====
     ALTER TABLE IF EXISTS place_mock
         ADD COLUMN IF NOT EXISTS place_id BIGINT;

     -- 타입이 bigint가 아니면 변환
     DO $$
     DECLARE v_typ TEXT;
     BEGIN
         SELECT data_type INTO v_typ
           FROM information_schema.columns
          WHERE table_schema='public' AND table_name='place_mock' AND column_name='place_id';

         IF v_typ IS NOT NULL AND v_typ NOT IN ('bigint') THEN
             ALTER TABLE place_mock
                 ALTER COLUMN place_id TYPE BIGINT USING place_id::BIGINT;
         END IF;
     END $$;

     -- 기존 제약 초기화 후 재생성
     ALTER TABLE place_mock DROP CONSTRAINT IF EXISTS place_mock_pkey;
     ALTER TABLE place_mock DROP CONSTRAINT IF EXISTS fk_place_mock_place;

     ALTER TABLE place_mock
         ADD CONSTRAINT place_mock_pkey PRIMARY KEY (place_id);

     ALTER TABLE place_mock
         ADD CONSTRAINT fk_place_mock_place
         FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE;


-- 3) 보조 인덱스(선택적 성능 보강)
CREATE INDEX IF NOT EXISTS idx_place_summaries_updated_at ON place_summaries(updated_at);
