-- Theme 1: 성능 최적화를 위한 인덱스 추가

-- 1. Requests 테이블: Group ID 기준 조회 성능 최적화 (가장 중요: Full Table Scan 방지)
CREATE INDEX idx_requests_group_id ON requests (group_id);

-- 2. Requests 테이블: Station Code 기준 조회 성능 최적화
CREATE INDEX idx_requests_station_code ON requests (station_code);

-- 3. Requests 테이블: Slug 기준 조회 최적화
-- (Unique Constraint로 암시적 인덱스가 있을 수 있으나, 명시적 인덱스 생성 시도)
CREATE INDEX IF NOT EXISTS idx_requests_slug ON requests (slug);

-- 4. Groups 테이블: Slug 기준 조회 최적화
CREATE INDEX IF NOT EXISTS idx_groups_slug ON groups (slug);