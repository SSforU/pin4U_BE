-- seed-perf-data.sql: 성능 테스트용 시드 데이터 (멱등)
-- 규모: 사용자 5건, 역 10건, 요청 50건, 장소 500건, 추천 집계 2500건

-- 1) 사용자
INSERT INTO users (id, nickname, preference_text, created_at, updated_at)
SELECT id, nickname, preference_text, now(), now()
FROM (VALUES
    (101, 'perf-user-1', '카페를 좋아합니다'),
    (102, 'perf-user-2', '맛집 탐방'),
    (103, 'perf-user-3', '디저트 위주'),
    (104, 'perf-user-4', '분위기 좋은 곳'),
    (105, 'perf-user-5', '혼밥 전문')
) AS t(id, nickname, preference_text)
ON CONFLICT (id) DO NOTHING;

-- 2) 역 (서울 주요역 10개)
INSERT INTO stations (code, name, line, lat, lng) VALUES
    ('S0201', '강남', '2호선', 37.4979, 127.0276),
    ('S0202', '역삼', '2호선', 37.5007, 127.0365),
    ('S0203', '선릉', '2호선', 37.5045, 127.0489),
    ('S0204', '삼성', '2호선', 37.5088, 127.0631),
    ('S0205', '잠실', '2호선', 37.5133, 127.1001),
    ('S0301', '압구정', '3호선', 37.5270, 127.0286),
    ('S0302', '신사', '3호선', 37.5160, 127.0199),
    ('S0701', '건대입구', '7호선', 37.5405, 127.0702),
    ('S0901', '신논현', '9호선', 37.5046, 127.0248),
    ('S0902', '언주', '9호선', 37.5073, 127.0341)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, line = EXCLUDED.line, lat = EXCLUDED.lat, lng = EXCLUDED.lng;

-- 3) 요청 50건
DO $$
DECLARE
    station_codes TEXT[] := ARRAY['S0201','S0202','S0203','S0204','S0205','S0301','S0302','S0701','S0901','S0902'];
    user_ids INT[] := ARRAY[101,102,103,104,105];
    i INT;
    slug_val TEXT;
    st_code TEXT;
    uid INT;
BEGIN
    FOR i IN 1..50 LOOP
        slug_val := 'perf-req-' || lpad(i::text, 3, '0');
        st_code := station_codes[1 + (i % 10)];
        uid := user_ids[1 + (i % 5)];
        INSERT INTO requests (slug, station_code, owner_user_id, request_message, created_at)
        VALUES (slug_val, st_code, uid, '성능 테스트 요청 ' || i, now() - (i || ' hours')::interval)
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;
END $$;

-- 4) 장소 500건
DO $$
DECLARE
    i INT;
    ext_id TEXT;
    place_name TEXT;
    names TEXT[] := ARRAY['카페','맛집','레스토랑','베이커리','술집','분식','피자','치킨','일식','한식',
                          '중식','양식','디저트','브런치','샐러드','버거','타코','스시','라멘','떡볶이'];
    base_x DOUBLE PRECISION := 127.027;
    base_y DOUBLE PRECISION := 37.498;
BEGIN
    FOR i IN 1..500 LOOP
        ext_id := 'kakao:PERF-' || lpad(i::text, 4, '0');
        place_name := names[1 + (i % 20)] || ' ' || i || '호점';
        INSERT INTO places (external_id, id_old, place_name, category_group_code, category_group_name,
                            category_name, x, y, created_at, updated_at)
        VALUES (ext_id, 'PERF' || i, place_name, 'FD6', '음식점',
                '음식점 > ' || names[1 + (i % 20)],
                (base_x + (random() * 0.05 - 0.025))::text,
                (base_y + (random() * 0.03 - 0.015))::text,
                now(), now())
        ON CONFLICT (external_id) DO NOTHING;
    END LOOP;
END $$;

-- 5) 요청-장소 집계 (요청당 10개 장소, 총 500건)
DO $$
DECLARE
    i INT;
    j INT;
    slug_val TEXT;
    pid BIGINT;
    ext_id TEXT;
BEGIN
    FOR i IN 1..50 LOOP
        slug_val := 'perf-req-' || lpad(i::text, 3, '0');
        FOR j IN 1..10 LOOP
            ext_id := 'kakao:PERF-' || lpad(((i - 1) * 10 + j)::text, 4, '0');
            SELECT p.id INTO pid FROM places p WHERE p.external_id = ext_id;
            IF pid IS NOT NULL THEN
                INSERT INTO request_place_aggregates (request_id, place_id, place_external_id, recommended_count,
                                                     first_recommended_at, last_recommended_at)
                VALUES (slug_val, pid, ext_id, (random() * 20)::int,
                        now() - '7 days'::interval, now())
                ON CONFLICT (request_id, place_id) DO NOTHING;
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- 검증 쿼리
SELECT 'users' AS t, count(*) FROM users WHERE id BETWEEN 101 AND 105
UNION ALL SELECT 'stations', count(*) FROM stations WHERE code LIKE 'S0%'
UNION ALL SELECT 'requests', count(*) FROM requests WHERE slug LIKE 'perf-req-%'
UNION ALL SELECT 'places', count(*) FROM places WHERE external_id LIKE 'kakao:PERF-%'
UNION ALL SELECT 'rpa', count(*) FROM request_place_aggregates
    WHERE request_id LIKE 'perf-req-%';
