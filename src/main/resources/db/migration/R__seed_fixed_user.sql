-- 고정 사용자(id=1) 멱등 시드/업서트 (값 보정 포함)
INSERT INTO users (id, nickname, preference_text, created_at, updated_at)
VALUES (
  1,
  '아기사자',
  '분위기 좋고 조용한 장소 선호',
  TIMESTAMPTZ '2025-08-16 00:00:00+00',
  TIMESTAMPTZ '2025-08-16 00:00:00+00'
)
ON CONFLICT (id) DO UPDATE
SET
  nickname        = EXCLUDED.nickname,         -- 항상 "아기사자"로 보정
  preference_text = EXCLUDED.preference_text,  -- 항상 고정 문구로 보정
  -- created_at은 유지(최초값 보존), updated_at만 갱신
  updated_at      = NOW();

-- 시퀀스(nextval) 위치를 항상 MAX(id) 이상으로 맞춤 (id=1 보장)
SELECT setval(
  pg_get_serial_sequence('users','id'),
  GREATEST((SELECT COALESCE(MAX(id), 0) FROM users), 1),
  true
);
