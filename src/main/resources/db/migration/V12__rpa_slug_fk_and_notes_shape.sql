-- PostgreSQL 전제
-- 1) request_place_aggregates: request_id → VARCHAR(slug FK), place_external_id → FK(places.external_id)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema='public' AND table_name='request_place_aggregates'
  ) THEN
    CREATE TABLE public.request_place_aggregates (
      id BIGSERIAL PRIMARY KEY,
      request_id VARCHAR(100) NOT NULL REFERENCES public.requests(slug) ON DELETE CASCADE,
      place_external_id VARCHAR(100) NOT NULL REFERENCES public.places(external_id) ON DELETE CASCADE,
      recommended_count INTEGER NOT NULL DEFAULT 0,
      first_recommended_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      last_recommended_at  TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE UNIQUE INDEX ux_req_place ON public.request_place_aggregates(request_id, place_external_id);
    CREATE INDEX idx_rpa_request ON public.request_place_aggregates(request_id);
    CREATE INDEX idx_rpa_external ON public.request_place_aggregates(place_external_id);
  ELSE
    -- 컬럼 존재/타입 정렬
    -- (a) request_id가 BIGINT이면 slug로 변환 컬럼 추가 후 백필
    IF EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='request_place_aggregates'
        AND column_name='request_id' AND data_type='bigint'
    ) THEN
      -- slug 컬럼 임시 추가
      IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='public' AND table_name='request_place_aggregates'
          AND column_name='request_slug'
      ) THEN
        ALTER TABLE public.request_place_aggregates ADD COLUMN request_slug VARCHAR(100);
      END IF;

      -- requests.id -> slug 매핑 백필
      UPDATE public.request_place_aggregates rpa
      SET request_slug = req.slug
      FROM public.requests req
      WHERE req.id::bigint = rpa.request_id
        AND (rpa.request_slug IS NULL OR rpa.request_slug <> req.slug);

      -- 기존 FK/INDEX 정리 및 교체
      ALTER TABLE public.request_place_aggregates DROP CONSTRAINT IF EXISTS request_place_aggregates_request_id_fkey;
      -- 컬럼 교체
      ALTER TABLE public.request_place_aggregates DROP COLUMN request_id;
      ALTER TABLE public.request_place_aggregates RENAME COLUMN request_slug TO request_id;
      -- 새 FK
      ALTER TABLE public.request_place_aggregates
        ADD CONSTRAINT request_place_aggregates_request_id_fkey
        FOREIGN KEY (request_id) REFERENCES public.requests(slug) ON DELETE CASCADE;
    END IF;

    -- (b) 장소 키를 place_external_id로 정규화
    IF EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='request_place_aggregates'
        AND column_name='external_id'
    ) AND NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='request_place_aggregates'
        AND column_name='place_external_id'
    ) THEN
      ALTER TABLE public.request_place_aggregates RENAME COLUMN external_id TO place_external_id;
    END IF;

    -- FK 보강
    ALTER TABLE public.request_place_aggregates
      DROP CONSTRAINT IF EXISTS request_place_aggregates_external_id_fkey;
    ALTER TABLE public.request_place_aggregates
      ADD CONSTRAINT request_place_aggregates_place_external_id_fkey
      FOREIGN KEY (place_external_id) REFERENCES public.places(external_id) ON DELETE CASCADE;

    -- recommended_count 컬럼/디폴트 일치화
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='request_place_aggregates'
        AND column_name='recommended_count'
    ) THEN
      -- 혹시 recommend_count로 존재하면 이름 정규화
      IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='public' AND table_name='request_place_aggregates'
          AND column_name='recommend_count'
      ) THEN
        ALTER TABLE public.request_place_aggregates RENAME COLUMN recommend_count TO recommended_count;
      ELSE
        ALTER TABLE public.request_place_aggregates ADD COLUMN recommended_count INTEGER NOT NULL DEFAULT 0;
      END IF;
    END IF;

    -- 타임스탬프 기본값 보정
    ALTER TABLE public.request_place_aggregates
      ALTER COLUMN first_recommended_at SET DEFAULT now();
    ALTER TABLE public.request_place_aggregates
      ALTER COLUMN last_recommended_at  SET DEFAULT now();

    -- 유니크/인덱스 정리
    DROP INDEX IF EXISTS uq_rpa_request_place;
    DROP INDEX IF EXISTS uq_req_place;
    DROP INDEX IF EXISTS ux_req_place;
    CREATE UNIQUE INDEX ux_req_place ON public.request_place_aggregates(request_id, place_external_id);
    CREATE INDEX IF NOT EXISTS idx_rpa_request ON public.request_place_aggregates(request_id);
    CREATE INDEX IF NOT EXISTS idx_rpa_external ON public.request_place_aggregates(place_external_id);
  END IF;
END$$;

-- 2) recommendation_notes: 존재하지 않으면 생성, 있으면 최소 인덱스 보강
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema='public' AND table_name='recommendation_notes'
  ) THEN
    CREATE TABLE public.recommendation_notes (
      id BIGSERIAL PRIMARY KEY,
      rpa_id BIGINT NOT NULL REFERENCES public.request_place_aggregates(id) ON DELETE CASCADE,
      nickname VARCHAR(50),
      recommend_message VARCHAR(300),
      image_url VARCHAR(300),
      tags JSONB,
      guest_id UUID NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      created_at_minute TIMESTAMPTZ,
      CONSTRAINT chk_notes_tags_valid CHECK (is_valid_note_tags(tags))
    );
    CREATE INDEX idx_notes_rpa ON public.recommendation_notes(rpa_id);
    CREATE INDEX idx_notes_guest_id_created_at ON public.recommendation_notes(guest_id, created_at);

    -- created_at_minute 유지 트리거
    CREATE OR REPLACE FUNCTION recommendation_notes_set_minute()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $fn$
    BEGIN
      NEW.created_at_minute := date_trunc('minute', NEW.created_at);
      RETURN NEW;
    END;
    $fn$;

    DROP TRIGGER IF EXISTS trg_recommendation_notes_set_minute ON public.recommendation_notes;
    CREATE TRIGGER trg_recommendation_notes_set_minute
    BEFORE INSERT OR UPDATE OF created_at ON public.recommendation_notes
    FOR EACH ROW
    EXECUTE FUNCTION recommendation_notes_set_minute();
  ELSE
    -- 최소 인덱스 보강(있으면 생성 안 됨)
    CREATE INDEX IF NOT EXISTS idx_notes_rpa ON public.recommendation_notes(rpa_id);
    CREATE INDEX IF NOT EXISTS idx_notes_guest_id_created_at ON public.recommendation_notes(guest_id, created_at);
  END IF;
END$$;
