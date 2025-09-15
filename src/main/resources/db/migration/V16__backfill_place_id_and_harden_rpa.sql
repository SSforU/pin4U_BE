-- V16__backfill_place_id_and_harden_rpa.sql
-- 목적: (재실행 안전) place_id 백필/중복병합은 이미 끝났어도 무해, 제약은 '있으면 스킵'

-- 1) place_id 백필 (이미 채워져 있으면 0건 업데이트)
UPDATE request_place_aggregates a
SET    place_id = p.id
FROM   places p
WHERE  a.place_id IS NULL
  AND  a.place_external_id IS NOT NULL
  AND  p.external_id = a.place_external_id;

-- 2) (request_id, place_id) 중복 병합 (이미 정리되어 있으면 0건)
CREATE TEMP TABLE rpa_merge ON COMMIT DROP AS
SELECT
  request_id,
  place_id,
  MIN(first_recommended_at) AS first_at,
  MAX(last_recommended_at)  AS last_at,
  SUM(recommended_count)    AS total_cnt,
  MIN(id)                   AS survivor_id
FROM request_place_aggregates
WHERE place_id IS NOT NULL
  AND request_id IS NOT NULL
GROUP BY request_id, place_id
HAVING COUNT(*) > 1;

UPDATE request_place_aggregates r
SET    recommended_count    = m.total_cnt,
       first_recommended_at = m.first_at,
       last_recommended_at  = m.last_at
FROM   rpa_merge m
WHERE  r.id = m.survivor_id;

DELETE FROM request_place_aggregates r
USING  rpa_merge m
WHERE  r.request_id = m.request_id
  AND  r.place_id   = m.place_id
  AND  r.id        <> m.survivor_id;

-- 2-보강) 고아행 제거 (데모 안정성)
DELETE FROM request_place_aggregates
WHERE place_id IS NULL
   OR request_id IS NULL;

-- 3) NOT NULL 강제 (이미 NOT NULL이면 그냥 통과)
ALTER TABLE request_place_aggregates
  ALTER COLUMN place_id   SET NOT NULL,
  ALTER COLUMN request_id SET NOT NULL;

-- 4) UNIQUE 제약: 이름이 'ux_req_place'인 인덱스/제약이 있으면 스킵
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'ux_req_place') THEN
    ALTER TABLE request_place_aggregates
      ADD CONSTRAINT ux_req_place UNIQUE (request_id, place_id);
  END IF;
END$$;

-- 5) FK(place_id → places.id) : 이미 같은 FK가 있으면 스킵
DO $$
BEGIN
  IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint c
      JOIN pg_class t ON t.oid = c.conrelid
      WHERE t.relname = 'request_place_aggregates'
        AND c.contype = 'f'
        AND pg_get_constraintdef(c.oid) ILIKE '%FOREIGN KEY (place_id)%REFERENCES places(id)%'
  ) THEN
      ALTER TABLE request_place_aggregates
        ADD CONSTRAINT fk_rpa_place
        FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE;
  END IF;
END$$;

-- 참고: place_external_id 컬럼은 지금 단계에선 유지(로그/디버깅용). 출시 전 완전 제거는 V17에서 DROP COLUMN 권장.
