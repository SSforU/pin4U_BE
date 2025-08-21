-- src/main/resources/db/migration/V8__drop_external_source_from_places.sql
ALTER TABLE places DROP COLUMN IF EXISTS external_source;
