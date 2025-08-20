-- =========================
-- V3: Align names & evidence, keep image_url
-- =========================

-- 1) requests: message -> request_message (있을 때만 변경)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'requests' AND column_name = 'message'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'requests' AND column_name = 'request_message'
    ) THEN
        EXECUTE 'ALTER TABLE requests RENAME COLUMN message TO request_message';
    END IF;
END$$;

-- 2) request_place_aggregates(RPA): 집계 컬럼 보장
--    - recommend_count 라는 과거 오타/대체명을 썼다면 recommended_count로 통일
DO $$
BEGIN
    -- rename recommend_count -> recommended_count (충돌 없을 때만)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'request_place_aggregates' AND column_name = 'recommend_count'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'request_place_aggregates' AND column_name = 'recommended_count'
    ) THEN
        EXECUTE 'ALTER TABLE request_place_aggregates RENAME COLUMN recommend_count TO recommended_count';
    END IF;

    -- 없으면 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'request_place_aggregates' AND column_name = 'recommended_count'
    ) THEN
        EXECUTE 'ALTER TABLE request_place_aggregates ADD COLUMN recommended_count INTEGER NOT NULL DEFAULT 0';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'request_place_aggregates' AND column_name = 'first_recommended_at'
    ) THEN
        EXECUTE 'ALTER TABLE request_place_aggregates ADD COLUMN first_recommended_at TIMESTAMPTZ NOT NULL DEFAULT NOW()';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'request_place_aggregates' AND column_name = 'last_recommended_at'
    ) THEN
        EXECUTE 'ALTER TABLE request_place_aggregates ADD COLUMN last_recommended_at TIMESTAMPTZ NOT NULL DEFAULT NOW()';
    END IF;
END$$;

-- 3) recommendation_notes: memo -> recommend_message, image_url 유지/보장
DO $$
BEGIN
    -- rename memo -> recommend_message
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'recommendation_notes' AND column_name = 'memo'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'recommendation_notes' AND column_name = 'recommend_message'
    ) THEN
        EXECUTE 'ALTER TABLE recommendation_notes RENAME COLUMN memo TO recommend_message';
    END IF;

    -- recommend_message 없으면 생성
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'recommendation_notes' AND column_name = 'recommend_message'
    ) THEN
        EXECUTE 'ALTER TABLE recommendation_notes ADD COLUMN recommend_message VARCHAR(300)';
    END IF;

    -- image_url 없으면 생성(업로드는 앱에서 처리 후 S3/GCS 등 절대경로 URL 저장)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'recommendation_notes' AND column_name = 'image_url'
    ) THEN
        EXECUTE 'ALTER TABLE recommendation_notes ADD COLUMN image_url VARCHAR(300)';
    END IF;
END$$;

-- 4) place_summaries.evidence: JSONB(최대 5개 요소)로 강제
--    - 이미 jsonb면 그대로 두고, text/varchar면 jsonb로 변환
DO $$
DECLARE
    coltype TEXT;
BEGIN
    SELECT data_type INTO coltype
    FROM information_schema.columns
    WHERE table_name = 'place_summaries' AND column_name = 'evidence';

    IF coltype IS NOT NULL AND coltype <> 'jsonb' THEN
        -- 문자열로 저장돼 왔다면 배열 문자열이면 캐스트, 아니면 단일 문자열을 배열로 감싸 변환
        -- (필요시 앱 레벨에서 균질화하는 게 가장 안전)
        BEGIN
            EXECUTE $sql$
                ALTER TABLE place_summaries
                ALTER COLUMN evidence TYPE jsonb
                USING (
                    CASE
                        WHEN trim(evidence) ~ '^\[.*\]$' THEN evidence::jsonb
                        ELSE to_jsonb(evidence)
                    END
                )
            $sql$;
        EXCEPTION WHEN others THEN
            -- 변환 실패 시, 마지막 안전장치: 단일 문자열로 to_jsonb 처리
            EXECUTE $sql$
                ALTER TABLE place_summaries
                ALTER COLUMN evidence TYPE jsonb
                USING to_jsonb(evidence)
            $sql$;
        END;
    END IF;

    -- 제약조건: evidence는 null 또는 길이<=5의 배열
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'place_summaries'
          AND constraint_name = 'chk_place_summaries_evidence_array_max5'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE place_summaries
            ADD CONSTRAINT chk_place_summaries_evidence_array_max5
            CHECK (
                evidence IS NULL
                OR (
                    jsonb_typeof(evidence) = 'array'
                    AND jsonb_array_length(evidence) <= 5
                )
            )
        $sql$;
    END IF;
END$$;

-- 5) places.place_url: 이미 존재하면 무시, 없으면 추가
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'places' AND column_name = 'place_url'
    ) THEN
        EXECUTE 'ALTER TABLE places ADD COLUMN place_url VARCHAR(500)';
    END IF;
END$$;
