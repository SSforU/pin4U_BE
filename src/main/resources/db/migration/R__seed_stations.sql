INSERT INTO stations (code, name, line, lat, lng) VALUES
('S0701', '숭실대입구', '7호선', 37.4962820, 126.9534910),
('S0222', '서울대입구', '2호선', 37.4812470, 126.9527390)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    line = EXCLUDED.line,
    lat  = EXCLUDED.lat,
    lng  = EXCLUDED.lng;
