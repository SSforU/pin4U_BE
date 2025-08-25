-- V14__post_v13_fixes.sql
-- 목적: V13 이후 남은 꼬임을 안전하게 정리 (멱등)

BEGIN;

-- place_summaries evidence 관련 CHECK/제약이 남아 있으면 제거
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT conname, pg_get_constraintdef(oid) AS def
    FROM pg_constraint
    WHERE conrelid = 'public.place_summaries'::regclass
      AND contype = 'c'
  LOOP
    IF r.def ILIKE '%evidence%' OR r.def ILIKE '%jsonb_typeof%' OR r.def ILIKE '%jsonb%' THEN
      EXECUTE format('ALTER TABLE public.place_summaries DROP CONSTRAINT %I', r.conname);
    END IF;
  END LOOP;
END$$;

-- evidence 를 varchar(200) 으로 통일
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='place_summaries' AND column_name='evidence'
  ) THEN
    EXECUTE 'ALTER TABLE public.place_summaries
             ALTER COLUMN evidence TYPE varchar(200)
             USING left(evidence::text, 200)';
  END IF;
END$$;

-- place_id 타입/FK/PK 보정
DO $$
DECLARE r record;
BEGIN
  -- place_id 를 bigint로 강제
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='place_summaries'
      AND column_name='place_id' AND data_type <> 'bigint'
  ) THEN
    EXECUTE 'ALTER TABLE public.place_summaries
             ALTER COLUMN place_id TYPE BIGINT
             USING NULLIF(place_id::text, '''')::BIGINT';
  END IF;

  -- PK 가 place_id 가 아니면 교체
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
    WHERE c.conrelid='public.place_summaries'::regclass AND c.contype='p' AND a.attname='place_id'
  ) THEN
    FOR r IN
      SELECT conname FROM pg_constraint
      WHERE conrelid='public.place_summaries'::regclass AND contype='p'
    LOOP
      EXECUTE format('ALTER TABLE public.place_summaries DROP CONSTRAINT %I', r.conname);
    END LOOP;

    EXECUTE 'ALTER TABLE public.place_summaries
             ADD CONSTRAINT place_summaries_pkey PRIMARY KEY (place_id)';
  END IF;

  -- FK 는 항상 places(id) 로
  FOR r IN
    SELECT conname
    FROM   pg_constraint
    WHERE  conrelid='public.place_summaries'::regclass
    AND    contype='f'
  LOOP
    EXECUTE format('ALTER TABLE public.place_summaries DROP CONSTRAINT %I', r.conname);
  END LOOP;

  EXECUTE 'ALTER TABLE public.place_summaries
           ADD CONSTRAINT fk_place_summaries_place
           FOREIGN KEY (place_id) REFERENCES public.places(id) ON DELETE CASCADE';
END$$;

COMMIT;
