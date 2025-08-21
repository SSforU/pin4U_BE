-- 코드가 기대하는 컬럼을 추가한다.
ALTER TABLE public.requests
  ADD COLUMN IF NOT EXISTS owner_nickname varchar(50),
  ADD COLUMN IF NOT EXISTS recommend_count integer NOT NULL DEFAULT 0;

-- slug로 중복이 생기지 않도록 유니크 인덱스(선택)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='ux_requests_slug'
  ) THEN
    EXECUTE 'CREATE UNIQUE INDEX ux_requests_slug ON public.requests (slug)';
  END IF;
END $$;
