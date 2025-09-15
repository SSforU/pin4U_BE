-- ============================================================
-- V17 (통합): users.kakao_user_id + groups / members / visits
--            + requests, recommendation_notes 보강
-- PostgreSQL / 모든 변경은 조건부(IF NOT EXISTS)로 안전 적용
-- ============================================================

-- ------------------------------------------------------------
-- 0) users: kakao_user_id (부분 UNIQUE)
-- ------------------------------------------------------------
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS kakao_user_id BIGINT;

-- NULL만 제외해서 유니크 보장 (중복 가입 방지)
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_kakao_id
  ON users(kakao_user_id)
  WHERE kakao_user_id IS NOT NULL;

-- ------------------------------------------------------------
-- A-1) groups
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS groups (
  id             BIGSERIAL PRIMARY KEY,
  slug           VARCHAR(24) NOT NULL UNIQUE,
  name           VARCHAR(32) NOT NULL,
  image_url      VARCHAR(1000),
  owner_user_id  BIGINT NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_groups_owner_user
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_groups_owner_user_id ON groups(owner_user_id);

-- ------------------------------------------------------------
-- A-2) group_members
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS group_members (
  group_id     BIGINT NOT NULL,
  user_id      BIGINT NOT NULL,
  role         VARCHAR(10) NOT NULL,
  status       VARCHAR(10) NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_at  TIMESTAMPTZ NULL,
  PRIMARY KEY (group_id, user_id),
  CONSTRAINT fk_gm_group  FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
  CONSTRAINT fk_gm_user   FOREIGN KEY (user_id)  REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT ck_gm_role   CHECK (role   IN ('owner','member')),
  CONSTRAINT ck_gm_status CHECK (status IN ('pending','approved'))
);
CREATE INDEX IF NOT EXISTS idx_gm_group_status ON group_members(group_id, status);
CREATE INDEX IF NOT EXISTS idx_gm_user         ON group_members(user_id);

-- ------------------------------------------------------------
-- A-3) group_visits
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS group_visits (
  group_id         BIGINT NOT NULL,
  user_id          BIGINT NOT NULL,
  first_visited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (group_id, user_id),
  CONSTRAINT fk_gv_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
  CONSTRAINT fk_gv_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE
);

-- ------------------------------------------------------------
-- B-1) requests: group_id 추가 + FK/인덱스
-- ------------------------------------------------------------
ALTER TABLE requests
  ADD COLUMN IF NOT EXISTS group_id BIGINT;

DO $$
BEGIN
  IF NOT EXISTS (
      SELECT 1
        FROM pg_constraint c
        JOIN pg_class      t  ON c.conrelid  = t.oid
        JOIN pg_namespace  n  ON n.oid       = t.relnamespace
        JOIN pg_class      rt ON c.confrelid = rt.oid
       WHERE n.nspname = 'public'
         AND t.relname = 'requests'
         AND rt.relname = 'groups'
         AND c.contype = 'f'
         AND c.conkey  = (SELECT ARRAY_AGG(attnum ORDER BY attnum)
                            FROM pg_attribute
                           WHERE attrelid = t.oid
                             AND attname  = 'group_id')
  ) THEN
    ALTER TABLE requests
      ADD CONSTRAINT requests_group_id_fkey
        FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_requests_group_id ON requests(group_id);

-- ------------------------------------------------------------
-- B-1') requests: owner_user_id FK 보강(+ NOT NULL 안전 처리)
-- ------------------------------------------------------------
ALTER TABLE requests
  ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

DO $$
BEGIN
  -- 데모 안전장치: NULL 있으면 임의의 첫 사용자로 백필
  IF EXISTS (SELECT 1 FROM information_schema.columns
              WHERE table_name='requests' AND column_name='owner_user_id')
     AND EXISTS (SELECT 1 FROM requests WHERE owner_user_id IS NULL)
     AND EXISTS (SELECT 1 FROM users) THEN
    UPDATE requests
       SET owner_user_id = (SELECT id FROM users ORDER BY id LIMIT 1)
     WHERE owner_user_id IS NULL;
  END IF;

  -- NOT NULL (이미 NOT NULL이면 통과)
  BEGIN
    ALTER TABLE requests ALTER COLUMN owner_user_id SET NOT NULL;
  EXCEPTION WHEN others THEN
    -- 실데이터에 NULL 남아있으면 이후 수동 보정 필요. 여기선 조용히 통과.
  END;

  -- FK 부재 시 추가
  IF NOT EXISTS (
      SELECT 1
        FROM pg_constraint c
        JOIN pg_class      t  ON c.conrelid  = t.oid
        JOIN pg_namespace  n  ON n.oid       = t.relnamespace
        JOIN pg_class      rt ON c.confrelid = rt.oid
       WHERE n.nspname = 'public'
         AND t.relname = 'requests'
         AND rt.relname = 'users'
         AND c.contype = 'f'
         AND c.conkey  = (SELECT ARRAY_AGG(attnum ORDER BY attnum)
                            FROM pg_attribute
                           WHERE attrelid = t.oid
                             AND attname  = 'owner_user_id')
  ) THEN
    ALTER TABLE requests
      ADD CONSTRAINT requests_owner_user_id_fkey
        FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_requests_owner_user_id ON requests(owner_user_id);

-- 요청 메시지 NOT NULL 보장 (존재 시만 처리)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
              WHERE table_name='requests' AND column_name='request_message') THEN
    UPDATE requests SET request_message = '' WHERE request_message IS NULL;
    BEGIN
      ALTER TABLE requests ALTER COLUMN request_message SET NOT NULL;
    EXCEPTION WHEN others THEN
      -- 이미 NOT NULL이거나 타입 차이 시 통과
    END;
  END IF;
END $$;

-- ------------------------------------------------------------
-- B-2) recommendation_notes: image_is_public
-- ------------------------------------------------------------
ALTER TABLE recommendation_notes
  ADD COLUMN IF NOT EXISTS image_is_public BOOLEAN;

UPDATE recommendation_notes
   SET image_is_public = TRUE
 WHERE image_is_public IS NULL;

ALTER TABLE recommendation_notes
  ALTER COLUMN image_is_public SET NOT NULL,
  ALTER COLUMN image_is_public SET DEFAULT TRUE;
