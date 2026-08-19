-- V22: request_place_aggregates에 낙관적 락용 version 컬럼 추가
ALTER TABLE request_place_aggregates ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
