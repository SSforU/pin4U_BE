-- ===========================================
-- V13__pin4u_final.sql  (fixed)
-- 목적:
-- 1) 앱(JPA)에서 기대하는 스키마로 1회에 정합화
-- 2) place_summaries.place_id = BIGINT, evidence = VARCHAR(200) 강제
-- 3) requests.request_message 폭을 500으로 보장, slug 64 보장
-- 4) request_place_aggregates는 request_id(BIGINT), place_id(BIGINT)로 정규화
-- 5) places는 BIGINT PK(id) 보장, external_id는 UNIQUE로 유지
-- 6) users/stations 최소 시드 멱등 처리
-- ===========================================

-- 0) 방해되는 의존 뷰 제거 (있을 경우)
DROP VIEW IF EXISTS public.v_place_full CASCADE;

-- 0-1) 유효 태그 검증 함수(멱등)
CREATE OR REPLACE FUNCTION public.is_valid_note_tags(p_tags jsonb)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT
        p_tags IS NULL
        OR (
            jsonb_typeof(p_tags) = 'array'
            AND jsonb_array_length(p_tags) <= 3
            AND (
                SELECT COALESCE(bool_and(elem IN (
                    '분위기 맛집','핫플','힐링 스팟','또간집','숨은 맛집','가성비 갑'
                )), TRUE)
                FROM jsonb_array_elements_text(p_tags) AS t(elem)
            )
        );
$$;

-- 1) stations
CREATE TABLE IF NOT EXISTS public.stations (
    code varchar(16) PRIMARY KEY,
    name varchar(100) NOT NULL,
    line varchar(50)  NOT NULL,
    lat  numeric(11,7) NOT NULL,
    lng  numeric(11,7) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_stations_name ON public.stations(name);

-- 2) users
CREATE TABLE IF NOT EXISTS public.users (
    id              BIGSERIAL PRIMARY KEY,
    nickname        VARCHAR(50)  NOT NULL,
    preference_text VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_users_nickname ON public.users(nickname);

-- 3) places (BIGINT PK 보장, external_id UNIQUE 보장, 좌표는 문자열 보관)
CREATE TABLE IF NOT EXISTS public.places (
    id                  BIGSERIAL PRIMARY KEY,
    external_id         VARCHAR(100),
    kakao_id            VARCHAR(50),
    place_name          VARCHAR(200) NOT NULL,
    category_group_code VARCHAR(10),
    category_group_name VARCHAR(50),
    category_name       VARCHAR(300),
    phone               VARCHAR(50),
    address_name        VARCHAR(300),
    road_address_name   VARCHAR(300),
    x                   VARCHAR(50)  NOT NULL,
    y                   VARCHAR(50)  NOT NULL,
    place_url           VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- places.id BIGINT 보장 (기존 문자열 id가 있던 경우 대비)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='places' AND column_name='id' AND data_type <> 'bigint'
  ) THEN
    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.places_id_seq';
    EXECUTE 'ALTER TABLE public.places ALTER COLUMN id TYPE BIGINT USING NULLIF(id::text, '''')::BIGINT';
    EXECUTE 'ALTER TABLE public.places ALTER COLUMN id SET DEFAULT nextval(''public.places_id_seq'')';
  END IF;

  -- PK가 id가 아닐 수도 있으면 재설정
  IF EXISTS (
    SELECT 1
    FROM   pg_constraint
    WHERE  conrelid = 'public.places'::regclass
    AND    contype = 'p'
  ) THEN
    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint c
      JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
      WHERE c.conrelid='public.places'::regclass AND c.contype='p' AND a.attname='id'
    ) THEN
      EXECUTE 'ALTER TABLE public.places DROP CONSTRAINT places_pkey';
      EXECUTE 'ALTER TABLE public.places ADD CONSTRAINT places_pkey PRIMARY KEY (id)';
    END IF;
  ELSE
    EXECUTE 'ALTER TABLE public.places ADD CONSTRAINT places_pkey PRIMARY KEY (id)';
  END IF;

  -- 시퀀스/디폴트 보정
  EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.places_id_seq';
  EXECUTE 'ALTER TABLE public.places ALTER COLUMN id SET DEFAULT nextval(''public.places_id_seq'')';
  EXECUTE 'UPDATE public.places SET id = nextval(''public.places_id_seq'') WHERE id IS NULL';
  EXECUTE 'SELECT setval(''public.places_id_seq'', COALESCE((SELECT MAX(id) FROM public.places), 0))';
  EXECUTE 'ALTER SEQUENCE public.places_id_seq OWNED BY public.places.id';
END$$ LANGUAGE plpgsql;

-- external_id UNIQUE 보장(값이 있으면)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='places' AND column_name='external_id'
  ) THEN
    EXECUTE 'ALTER TABLE public.places ADD COLUMN external_id varchar(100)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.places'::regclass AND conname = 'ux_places_external_id'
  ) THEN
    EXECUTE $SQL$
      WITH dups AS (
        SELECT external_id
        FROM public.places
        WHERE external_id IS NOT NULL
        GROUP BY external_id
        HAVING COUNT(*) > 1
      ),
      victims AS (
        SELECT p.ctid
        FROM public.places p
        JOIN dups d ON p.external_id = d.external_id
        WHERE p.ctid NOT IN (
          SELECT MIN(p2.ctid)
          FROM public.places p2
          WHERE p2.external_id = p.external_id
          GROUP BY p2.external_id
        )
      )
      DELETE FROM public.places WHERE ctid IN (SELECT ctid FROM victims)
    $SQL$;

    EXECUTE 'ALTER TABLE public.places ADD CONSTRAINT ux_places_external_id UNIQUE (external_id)';
  END IF;
END$$ LANGUAGE plpgsql;

-- 4) place_mock
CREATE TABLE IF NOT EXISTS public.place_mock (
  external_id VARCHAR(100) PRIMARY KEY
    REFERENCES public.places(external_id) ON DELETE CASCADE,
  rating NUMERIC(2,1),
  rating_count INT,
  review_snippets JSONB,
  image_urls JSONB,
  opening_hours JSONB,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5) place_summaries : place_id(BIGINT) + evidence VARCHAR(200)
CREATE TABLE IF NOT EXISTS public.place_summaries (
    place_id          BIGINT PRIMARY KEY
      REFERENCES public.places(id) ON DELETE CASCADE,
    summary_text      TEXT,
    place_name        VARCHAR(200),
    road_address_name VARCHAR(300),
    phone             VARCHAR(50),
    opening_hours     JSONB,
    evidence          VARCHAR(200),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- place_summaries 정규화/제약 보정
DO $$
DECLARE
  r RECORD;
  pk_on_place_id BOOLEAN;
BEGIN
  -- external_id → place_id 백필
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='place_summaries' AND column_name='external_id'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='place_summaries' AND column_name='place_id'
  ) THEN
    EXECUTE 'ALTER TABLE public.place_summaries ADD COLUMN place_id BIGINT';
    EXECUTE $SQL$
      UPDATE public.place_summaries ps
      SET place_id = p.id
      FROM public.places p
      WHERE ps.external_id = p.external_id
        AND ps.place_id IS NULL
    $SQL$;
  END IF;

  -- place_id 타입 보정
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='place_summaries' AND column_name='place_id' AND data_type <> 'bigint'
  ) THEN
    EXECUTE 'ALTER TABLE public.place_summaries ALTER COLUMN place_id TYPE BIGINT USING NULLIF(place_id::text, '''')::BIGINT';
  END IF;

  -- evidence VARCHAR(200) 통일
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='place_summaries' AND column_name='evidence'
      AND (data_type <> 'character varying' OR (character_maximum_length IS NULL OR character_maximum_length > 200))
  ) THEN
    EXECUTE 'ALTER TABLE public.place_summaries ALTER COLUMN evidence TYPE varchar(200) USING left(evidence::text, 200)';
  END IF;

  -- PK가 place_id인지 확인 후 아니면 드롭하고 재생성
  SELECT EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
    WHERE c.conrelid='public.place_summaries'::regclass
      AND c.contype='p'
      AND a.attname='place_id'
  ) INTO pk_on_place_id;

  IF NOT pk_on_place_id THEN
    FOR r IN
      SELECT conname FROM pg_constraint
      WHERE conrelid='public.place_summaries'::regclass AND contype='p'
    LOOP
      EXECUTE format('ALTER TABLE public.place_summaries DROP CONSTRAINT %I', r.conname);
    END LOOP;

    EXECUTE 'ALTER TABLE public.place_summaries ADD CONSTRAINT place_summaries_pkey PRIMARY KEY (place_id)';
  END IF;

  -- FK 재정의(기존 FK 모두 제거 후 재생성)
  FOR r IN
    SELECT conname
    FROM   pg_constraint
    WHERE  conrelid='public.place_summaries'::regclass
    AND    contype='f'
  LOOP
    EXECUTE format('ALTER TABLE public.place_summaries DROP CONSTRAINT %I', r.conname);
  END LOOP;

  EXECUTE 'ALTER TABLE public.place_summaries ADD CONSTRAINT fk_place_summaries_place FOREIGN KEY (place_id) REFERENCES public.places(id) ON DELETE CASCADE';
END$$ LANGUAGE plpgsql;

-- 6) requests (폭/제약 보정)
CREATE TABLE IF NOT EXISTS public.requests (
    id               BIGSERIAL PRIMARY KEY,
    slug             VARCHAR(64) NOT NULL UNIQUE,
    owner_user_id    BIGINT      NOT NULL DEFAULT 1 REFERENCES public.users(id),
    station_code     VARCHAR(20) NOT NULL REFERENCES public.stations(code),
    request_message  VARCHAR(500) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='requests' AND column_name='slug' AND character_maximum_length < 64
  ) THEN
    EXECUTE 'ALTER TABLE public.requests ALTER COLUMN slug TYPE VARCHAR(64)';
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='requests' AND column_name='request_message'
      AND (data_type <> 'character varying' OR character_maximum_length < 500)
  ) THEN
    EXECUTE 'ALTER TABLE public.requests ALTER COLUMN request_message TYPE VARCHAR(500)';
  END IF;
END$$ LANGUAGE plpgsql;

CREATE INDEX IF NOT EXISTS idx_requests_owner_created ON public.requests(owner_user_id, created_at DESC);

-- 7) request_place_aggregates
CREATE TABLE IF NOT EXISTS public.request_place_aggregates (
  id BIGSERIAL PRIMARY KEY,
  request_id BIGINT NOT NULL REFERENCES public.requests(id) ON DELETE CASCADE,
  place_id   BIGINT NOT NULL REFERENCES public.places(id)   ON DELETE CASCADE,
  recommended_count INTEGER NOT NULL DEFAULT 0,
  first_recommended_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_recommended_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
  -- request_id 정규화
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='request_place_aggregates' AND column_name='request_id' AND data_type <> 'bigint'
  ) OR EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='request_place_aggregates' AND column_name='request_slug'
  ) THEN
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='request_place_aggregates' AND column_name='request_id_new'
    ) THEN
      EXECUTE 'ALTER TABLE public.request_place_aggregates ADD COLUMN request_id_new BIGINT';
    END IF;

    IF EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='request_place_aggregates' AND column_name='request_slug'
    ) THEN
      EXECUTE $SQL$
        UPDATE public.request_place_aggregates rpa
        SET request_id_new = req.id
        FROM public.requests req
        WHERE rpa.request_slug = req.slug AND rpa.request_id_new IS NULL
      $SQL$;
    ELSE
      EXECUTE $SQL$
        UPDATE public.request_place_aggregates rpa
        SET request_id_new = req.id
        FROM public.requests req
        WHERE (rpa.request_id::text = req.slug) AND rpa.request_id_new IS NULL
      $SQL$;
    END IF;

    EXECUTE 'ALTER TABLE public.request_place_aggregates DROP COLUMN IF EXISTS request_id';
    EXECUTE 'ALTER TABLE public.request_place_aggregates RENAME COLUMN request_id_new TO request_id';
    EXECUTE 'ALTER TABLE public.request_place_aggregates DROP CONSTRAINT IF EXISTS request_place_aggregates_request_id_fkey';
    EXECUTE 'ALTER TABLE public.request_place_aggregates ADD CONSTRAINT request_place_aggregates_request_id_fkey FOREIGN KEY (request_id) REFERENCES public.requests(id) ON DELETE CASCADE';
  END IF;

  -- place_id 정규화
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='request_place_aggregates' AND column_name='place_id'
  ) THEN
    EXECUTE 'ALTER TABLE public.request_place_aggregates ADD COLUMN place_id BIGINT';
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='request_place_aggregates' AND column_name='external_id'
  ) THEN
    EXECUTE $SQL$
      UPDATE public.request_place_aggregates rpa
      SET place_id = p.id
      FROM public.places p
      WHERE rpa.external_id = p.external_id AND rpa.place_id IS NULL
    $SQL$;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='request_place_aggregates' AND column_name='place_external_id'
  ) THEN
    EXECUTE $SQL$
      UPDATE public.request_place_aggregates rpa
      SET place_id = p.id
      FROM public.places p
      WHERE rpa.place_external_id = p.external_id AND rpa.place_id IS NULL
    $SQL$;
  END IF;

  EXECUTE 'ALTER TABLE public.request_place_aggregates ALTER COLUMN place_id TYPE BIGINT USING NULLIF(place_id::text, '''')::BIGINT';
  EXECUTE 'ALTER TABLE public.request_place_aggregates DROP CONSTRAINT IF EXISTS request_place_aggregates_place_id_fkey';
  EXECUTE 'ALTER TABLE public.request_place_aggregates ADD CONSTRAINT request_place_aggregates_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.places(id) ON DELETE CASCADE';

  DROP INDEX IF EXISTS ux_req_place;
  DROP INDEX IF EXISTS uq_req_place;
  DROP INDEX IF EXISTS uq_rpa_request_place;
  CREATE UNIQUE INDEX IF NOT EXISTS ux_req_place ON public.request_place_aggregates(request_id, place_id);
  CREATE INDEX IF NOT EXISTS idx_rpa_request ON public.request_place_aggregates(request_id);
  CREATE INDEX IF NOT EXISTS idx_rpa_place   ON public.request_place_aggregates(place_id);
END$$ LANGUAGE plpgsql;

-- 8) recommendation_notes
CREATE TABLE IF NOT EXISTS public.recommendation_notes (
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
CREATE INDEX IF NOT EXISTS idx_notes_rpa ON public.recommendation_notes(rpa_id);
CREATE INDEX IF NOT EXISTS idx_notes_guest_id_created_at ON public.recommendation_notes(guest_id, created_at);

CREATE OR REPLACE FUNCTION public.recommendation_notes_set_minute()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.created_at_minute := date_trunc('minute', NEW.created_at);
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_recommendation_notes_set_minute ON public.recommendation_notes;
CREATE TRIGGER trg_recommendation_notes_set_minute
BEFORE INSERT OR UPDATE OF created_at ON public.recommendation_notes
FOR EACH ROW
EXECUTE FUNCTION public.recommendation_notes_set_minute();

-- 9) 최소 시드 (멱등)
INSERT INTO public.users (id, nickname, preference_text, created_at, updated_at)
VALUES (1, '아기사자', '분위기 좋고 조용한 장소 선호', NOW(), NOW())
ON CONFLICT (id) DO UPDATE
SET nickname        = EXCLUDED.nickname,
    preference_text = EXCLUDED.preference_text,
    updated_at      = NOW();

SELECT setval(
  pg_get_serial_sequence('public.users','id'),
  GREATEST((SELECT COALESCE(MAX(id), 0) FROM public.users), 1),
  true
);

INSERT INTO public.stations (code, name, line, lat, lng) VALUES
('S0701', '숭실대입구', '7호선', 37.4962820, 126.9534910),
('S0222', '서울대입구', '2호선', 37.4812470, 126.9527390)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    line = EXCLUDED.line,
    lat  = EXCLUDED.lat,
    lng  = EXCLUDED.lng;
