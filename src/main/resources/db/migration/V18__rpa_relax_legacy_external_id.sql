-- V18__rpa_relax_legacy_external_id.sql
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public'
       AND table_name='request_place_aggregates'
       AND column_name='place_external_id'
  ) THEN
    BEGIN
      EXECUTE 'ALTER TABLE public.request_place_aggregates ALTER COLUMN place_external_id DROP NOT NULL';
    EXCEPTION WHEN others THEN
      RAISE NOTICE 'skip dropping NOT NULL on request_place_aggregates.place_external_id';
    END;
  END IF;
END $$;
