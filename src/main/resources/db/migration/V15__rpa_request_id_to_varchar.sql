-- V15: request_place_aggregates.request_id → VARCHAR(slug FK)
BEGIN;

-- 1) 기존 FK 제거(있다면)
ALTER TABLE public.request_place_aggregates
  DROP CONSTRAINT IF EXISTS request_place_aggregates_request_id_fkey;

-- 2) 컬럼 타입 변경: BIGINT → VARCHAR(100)
ALTER TABLE public.request_place_aggregates
  ALTER COLUMN request_id TYPE VARCHAR(100) USING request_id::text;

-- 3-a) 백필: 숫자(id) → slug로 치환
UPDATE public.request_place_aggregates rpa
SET request_id = r.slug
FROM public.requests r
WHERE r.id::text = rpa.request_id
  AND r.slug IS NOT NULL;

-- 3-b) (안전망) 이미 slug가 들어있는 행도 정합성 보장
UPDATE public.request_place_aggregates rpa
SET request_id = r.slug
FROM public.requests r
WHERE r.slug = rpa.request_id;

-- 4) slug 유니크 보장(없으면 추가)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_requests_slug') THEN
    ALTER TABLE public.requests
      ADD CONSTRAINT uq_requests_slug UNIQUE (slug);
  END IF;
END $$;

-- 5) FK 재연결: rpa.request_id → requests.slug
ALTER TABLE public.request_place_aggregates
  ADD CONSTRAINT request_place_aggregates_request_id_fkey
  FOREIGN KEY (request_id)
  REFERENCES public.requests(slug)
  ON DELETE CASCADE;

-- 6) 인덱스(권장)
DO $$
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM pg_indexes
      WHERE schemaname='public' AND tablename='request_place_aggregates' AND indexname='idx_rpa_request_id'
  ) THEN
    CREATE INDEX idx_rpa_request_id ON public.request_place_aggregates(request_id);
  END IF;
END $$;

COMMIT;
