-- src/main/resources/db/migration/V3__seed_user.sql
INSERT INTO users (id, nickname, preference_text, created_at, updated_at)
VALUES (1, '아기사자', '분위기 좋고 조용한 장소 선호', now(), now())
ON CONFLICT (id) DO NOTHING;
