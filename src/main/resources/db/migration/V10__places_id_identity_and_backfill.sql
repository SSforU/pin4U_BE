-- 1) id 타입을 BIGINT로 (필요할 때만)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'places' AND column_name = 'id' AND data_type <> 'bigint'
  ) THEN
    EXECUTE 'ALTER TABLE places ALTER COLUMN id TYPE BIGINT USING NULLIF(id, '''')::BIGINT';
  END IF;
END$$;

-- 2) 시퀀스 없으면 생성
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relkind='S' AND relname='places_id_seq') THEN
    CREATE SEQUENCE places_id_seq;
  END IF;
END$$;

-- 3) id 기본값(자동증가) 부여
ALTER TABLE places
  ALTER COLUMN id SET DEFAULT nextval('places_id_seq');

-- 4) 기존 NULL id들 채워 넣기
UPDATE places
   SET id = nextval('places_id_seq')
 WHERE id IS NULL;

-- 5) 시퀀스 값을 최댓값에 맞춤
SELECT setval('places_id_seq', COALESCE((SELECT MAX(id) FROM places), 0));

-- 6) 시퀀스를 컬럼에 소유권 연결(정리용)
ALTER SEQUENCE places_id_seq OWNED BY places.id;
