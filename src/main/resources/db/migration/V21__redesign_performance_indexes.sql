-- V21: 인덱스 재설계
-- V20이 생성한 인덱스 4개는 모두 기존 인덱스/제약과 중복이거나 단일 컬럼이라 실질 효과 없음.
-- 실제 쿼리 술어 기반으로 복합 인덱스를 새로 설계한다.

-- 1) V20 중복 인덱스 정리
DROP INDEX IF EXISTS idx_requests_slug;          -- UNIQUE 제약으로 btree 이미 존재
DROP INDEX IF EXISTS idx_groups_slug;            -- UNIQUE 제약으로 btree 이미 존재
-- idx_requests_group_id: V17에서 생성, V20에서 IF NOT EXISTS로 no-op
-- idx_requests_station_code: V1의 idx_requests_station과 동일 컬럼

-- 2) 핀 조회 (RequestDetailQueryRepository): WHERE request_id = ? ORDER BY recommended_count DESC
CREATE INDEX IF NOT EXISTS idx_rpa_request_recommend
    ON request_place_aggregates (request_id, recommended_count DESC);

-- 3) 홈 대시보드: WHERE owner_user_id = ? AND group_id IS NULL ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_requests_owner_personal
    ON requests (owner_user_id, created_at DESC) WHERE group_id IS NULL;

-- 4) 그룹 내 요청 목록: WHERE group_id = ? ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_requests_group_created
    ON requests (group_id, created_at DESC) WHERE group_id IS NOT NULL;

-- 5) 역 검색: lower(name) LIKE '%강남%' → 선행 와일드카드는 btree 불가, trigram 필요
-- 운영 환경에서는 CREATE INDEX CONCURRENTLY 사용 권장 (Flyway 트랜잭션 비활성화 필요)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_stations_name_trgm
    ON stations USING gin (lower(name) gin_trgm_ops);
