-- V7__ensure_places_y_and_seed_place_mock.sql
-- 목적:
-- 1) places.y(위도) 컬럼 보강
-- 2) places.external_id 에 유니크 보장 (ON CONFLICT 용)
-- 3) places 를 먼저 시드/업서트
-- 4) place_mock 는 실제 스키마에 맞는 칼럼만 업서트 (FK 오류 방지)

--------------------------------------------
-- 1) places.y 컬럼 추가 (카카오 원본 스키마와 동일하게 VARCHAR)
--------------------------------------------
ALTER TABLE public.places
    ADD COLUMN IF NOT EXISTS y VARCHAR(50);

--------------------------------------------
-- 2) places.external_id 유니크 보장
--    (중복이 있다면 제약 추가 시 실패하므로, 먼저 중복 제거)
--------------------------------------------
-- 외부 ID가 중복된 경우, 임의로 첫 레코드만 남기고 제거
WITH dups AS (
    SELECT external_id
    FROM public.places
    GROUP BY external_id
    HAVING COUNT(*) > 1
),
victims AS (
    SELECT p.ctid
    FROM public.places p
    JOIN dups d ON p.external_id = d.external_id
    -- 같은 external_id 중 첫번째만 남기고 나머지 삭제
    -- (정렬 기준이 없으므로 ctid 기준으로 남김)
    WHERE p.ctid NOT IN (
        SELECT MIN(p2.ctid)
        FROM public.places p2
        WHERE p2.external_id = p.external_id
        GROUP BY p2.external_id
    )
)
DELETE FROM public.places WHERE ctid IN (SELECT ctid FROM victims);

-- 유니크 제약이 없으면 추가
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM   pg_constraint
        WHERE  conname = 'ux_places_external_id'
        AND    conrelid = 'public.places'::regclass
    ) THEN
        ALTER TABLE public.places
            ADD CONSTRAINT ux_places_external_id UNIQUE (external_id);
    END IF;
END $$;

--------------------------------------------
-- 3) places 업서트 (먼저 선행)
--    places 테이블의 NOT NULL: external_id, id, place_name, x
--------------------------------------------
-- mock-001
INSERT INTO public.places (
    external_id, id, place_name,
    category_group_code, category_group_name, category_name,
    phone, address_name, road_address_name,
    x, y
) VALUES (
    'mock-001', 'mock-001', '테스트 맛집 1',
    'FD6', '음식점', '한식',
    NULL, NULL, NULL,
    '126.9530000', '37.4960000'
)
ON CONFLICT (external_id) DO UPDATE SET
    id               = EXCLUDED.id,
    place_name       = EXCLUDED.place_name,
    category_group_code  = EXCLUDED.category_group_code,
    category_group_name  = EXCLUDED.category_group_name,
    category_name        = EXCLUDED.category_name,
    phone            = EXCLUDED.phone,
    address_name     = EXCLUDED.address_name,
    road_address_name= EXCLUDED.road_address_name,
    x                = EXCLUDED.x,
    y                = EXCLUDED.y;

-- mock-002
INSERT INTO public.places (
    external_id, id, place_name,
    category_group_code, category_group_name, category_name,
    phone, address_name, road_address_name,
    x, y
) VALUES (
    'mock-002', 'mock-002', '테스트 카페 1',
    'CE7', '카페', '카페',
    NULL, NULL, NULL,
    '126.9522000', '37.4975000'
)
ON CONFLICT (external_id) DO UPDATE SET
    id               = EXCLUDED.id,
    place_name       = EXCLUDED.place_name,
    category_group_code  = EXCLUDED.category_group_code,
    category_group_name  = EXCLUDED.category_group_name,
    category_name        = EXCLUDED.category_name,
    phone            = EXCLUDED.phone,
    address_name     = EXCLUDED.address_name,
    road_address_name= EXCLUDED.road_address_name,
    x                = EXCLUDED.x,
    y                = EXCLUDED.y;

--------------------------------------------
-- 4) place_mock 업서트 (실제 스키마 컬럼만 사용)
--    현재 스키마: external_id(PK), rating, rating_count, review_snippets jsonb,
--    image_urls jsonb, opening_hours jsonb, updated_at timestamptz default now()
--    FK(있는 경우) 충족을 위해 places 시드 후에 실행
--------------------------------------------
INSERT INTO public.place_mock (external_id, rating, rating_count, updated_at)
VALUES
    ('mock-001', 4.5, 12, now()),
    ('mock-002', 4.2, 7,  now())
ON CONFLICT (external_id) DO UPDATE SET
    rating       = EXCLUDED.rating,
    rating_count = EXCLUDED.rating_count,
    updated_at   = now();

-- (선택) 조회 성능 인덱스는 실제 질의 계획 보고 추가
-- CREATE INDEX IF NOT EXISTS ix_places_name ON public.places (place_name);
