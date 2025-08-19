-- 역(stations) 스키마 정의. 기존에 없으면 생성.
CREATE TABLE IF NOT EXISTS stations (
  code VARCHAR(32) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  line VARCHAR(50)  NOT NULL,
  lat  DOUBLE PRECISION NOT NULL,
  lng  DOUBLE PRECISION NOT NULL
);

-- 검색 성능 보강(부분 검색용)
CREATE INDEX IF NOT EXISTS idx_stations_name ON stations(name);
