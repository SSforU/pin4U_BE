-- widen slug from 16 to 64
ALTER TABLE public.requests
  ALTER COLUMN slug TYPE VARCHAR(64);
