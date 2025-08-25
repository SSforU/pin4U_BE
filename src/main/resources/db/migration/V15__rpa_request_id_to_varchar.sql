-- V15__rpa_request_id_to_varchar.sql
-- bigint → varchar(100)로 전환 (기존 값은 ::text로 안전 변환)
ALTER TABLE request_place_aggregates
  ALTER COLUMN request_id TYPE varchar(100)
  USING request_id::text;
