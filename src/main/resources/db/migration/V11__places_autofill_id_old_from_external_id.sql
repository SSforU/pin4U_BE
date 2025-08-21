-- V11__places_autofill_id_old_from_external_id.sql
-- 목적: 신규 insert 시 id_old가 비어오면 external_id로 자동 채움.
-- 효과: id_old의 NOT NULL 제약을 유지하면서 애플리케이션 수정 없이 동작 보장.

BEGIN;

-- 혹시 남아있는 NULL을 정리 (정상이라면 0건)
UPDATE public.places
SET id_old = external_id
WHERE id_old IS NULL;

-- 트리거 함수: id_old가 비었으면 external_id로 채움
CREATE OR REPLACE FUNCTION public.trg_fill_id_old()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.id_old IS NULL OR NEW.id_old = '' THEN
    NEW.id_old := NEW.external_id;
  END IF;
  RETURN NEW;
END;
$$;

-- 트리거가 없을 때만 생성 (idempotent)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgname = 'trg_places_fill_id_old'
      AND tgrelid = 'public.places'::regclass
  ) THEN
    CREATE TRIGGER trg_places_fill_id_old
    BEFORE INSERT ON public.places
    FOR EACH ROW
    EXECUTE FUNCTION public.trg_fill_id_old();
  END IF;
END$$;

-- (권장) upsert 안정성을 위한 external_id 유니크 제약
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.places'::regclass
      AND conname  = 'uq_places_external_id'
  ) THEN
    ALTER TABLE public.places
      ADD CONSTRAINT uq_places_external_id UNIQUE (external_id);
  END IF;
END$$;

COMMIT;
