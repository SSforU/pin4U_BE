-- V9__fix_places_id_identity.sql
-- places.id(문자열)을 유지 보관(id_old)하고, 새 BIGINT PK(id)로 스왑.
-- 모든 FK/뷰 의존성을 자동으로 처리한다.

DO $do$
DECLARE
  v_viewdef TEXT;
  r RECORD;
BEGIN
  -- 0) v_place_full 뷰 정의 백업 후 드롭(있으면)
  IF EXISTS (
    SELECT 1
    FROM   pg_class c
    JOIN   pg_namespace n ON n.oid = c.relnamespace
    WHERE  c.relkind = 'v'
    AND    c.relname = 'v_place_full'
    AND    n.nspname = 'public'
  ) THEN
    SELECT pg_get_viewdef('public.v_place_full'::regclass, true) INTO v_viewdef;
    EXECUTE 'DROP VIEW public.v_place_full';
  END IF;

  -- 1) places를 참조하는 모든 FK 수집 -> 드롭
  CREATE TEMP TABLE _fk_backup (
    tbl regclass,
    name text,
    def  text
  ) ON COMMIT DROP;

  FOR r IN
    SELECT conrelid::regclass AS tbl,
           conname           AS name,
           pg_get_constraintdef(oid) AS def
    FROM pg_constraint
    WHERE contype = 'f'
      AND confrelid = 'public.places'::regclass
  LOOP
    INSERT INTO _fk_backup VALUES (r.tbl, r.name, r.def);
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.name);
  END LOOP;

  -- 2) 새 BIGINT 컬럼 추가 + 시퀀스 DEFAULT + 값 채우기
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='places' AND column_name='id_new'
  ) THEN
    EXECUTE 'ALTER TABLE public.places ADD COLUMN id_new BIGINT';
  END IF;

  EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.places_id_seq';
  EXECUTE 'ALTER SEQUENCE public.places_id_seq OWNED BY public.places.id_new';
  EXECUTE 'ALTER TABLE public.places ALTER COLUMN id_new SET DEFAULT nextval(''public.places_id_seq'')';
  EXECUTE 'UPDATE public.places SET id_new = DEFAULT WHERE id_new IS NULL';
  EXECUTE 'ALTER TABLE public.places ALTER COLUMN id_new SET NOT NULL';

  -- 3) PK를 id_new로 교체
  IF EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'places_pkey' AND conrelid = 'public.places'::regclass
  ) THEN
    EXECUTE 'ALTER TABLE public.places DROP CONSTRAINT places_pkey';
  END IF;
  EXECUTE 'ALTER TABLE public.places ADD CONSTRAINT places_pkey PRIMARY KEY (id_new)';

  -- 4) 컬럼 이름 스왑: 기존 문자열 id -> id_old, id_new -> id
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='places' AND column_name='id'
  ) THEN
    EXECUTE 'ALTER TABLE public.places RENAME COLUMN id TO id_old';
  END IF;
  EXECUTE 'ALTER TABLE public.places RENAME COLUMN id_new TO id';

  -- (선택) 시퀀스 소유주를 새 컬럼명으로 정정
  EXECUTE 'ALTER SEQUENCE public.places_id_seq OWNED BY public.places.id';

  -- 5) FK 복구 (원래 정의 그대로)
  FOR r IN SELECT * FROM _fk_backup LOOP
    EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', r.tbl, r.name, r.def);
  END LOOP;

  -- 6) 뷰 복구 (있었던 경우에만)
  IF v_viewdef IS NOT NULL THEN
    EXECUTE 'CREATE OR REPLACE VIEW public.v_place_full AS ' || v_viewdef;
  END IF;
END
$do$;
