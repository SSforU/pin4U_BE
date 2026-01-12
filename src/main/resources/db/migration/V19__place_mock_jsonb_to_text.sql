-- V19: place_mock의 jsonb -> text 전환 (이미 varchar/text면 건너뜀)

DO $$
BEGIN
  -- image_urls
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name   = 'place_mock'
      AND column_name  = 'image_urls'
      AND data_type    = 'jsonb'
  ) THEN
    ALTER TABLE public.place_mock
      ALTER COLUMN image_urls TYPE text USING image_urls::text;
  END IF;

  -- opening_hours
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name   = 'place_mock'
      AND column_name  = 'opening_hours'
      AND data_type    = 'jsonb'
  ) THEN
    ALTER TABLE public.place_mock
      ALTER COLUMN opening_hours TYPE text USING opening_hours::text;
  END IF;

  -- review_snippets
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name   = 'place_mock'
      AND column_name  = 'review_snippets'
      AND data_type    = 'jsonb'
  ) THEN
    ALTER TABLE public.place_mock
      ALTER COLUMN review_snippets TYPE text USING review_snippets::text;
  END IF;
END $$;
