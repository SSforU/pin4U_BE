BEGIN;

DROP TABLE IF EXISTS temp_stations;
CREATE TEMP TABLE temp_stations (
  code varchar(16),
  name varchar(100),
  line varchar(50),
  lat numeric(11,7),
  lng numeric(11,7)
);

\copy temp_stations FROM 'tools/stations_for_pin4u.csv' CSV HEADER;

INSERT INTO stations(code,name,line,lat,lng)
SELECT code,name,line,lat,lng FROM temp_stations
ON CONFLICT (code) DO UPDATE
SET name=EXCLUDED.name,
    line=EXCLUDED.line,
    lat =EXCLUDED.lat,
    lng =EXCLUDED.lng;

COMMIT;
