-- V12_9__pre_v13_cleanup.sql
-- 목적: V13 실행 전에 place_summaries.evidence 관련 남아있는 CHECK/제약을 제거 (idempotent)

BEGIN;

DO $$
DECLARE r record;
BEGIN
  -- place_summaries 에 달린 CHECK 제약들 중 evidence/jsonb 관련된 것만 제거
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

COMMIT;
