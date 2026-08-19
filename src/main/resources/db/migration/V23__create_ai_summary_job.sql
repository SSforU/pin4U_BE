-- V23: AI 요약 작업 상태 추적 테이블
CREATE TABLE IF NOT EXISTS ai_summary_job (
    id              BIGSERIAL PRIMARY KEY,
    request_slug    VARCHAR(64) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_ai_summary_job_slug UNIQUE (request_slug)
);

CREATE INDEX IF NOT EXISTS idx_ai_summary_job_status ON ai_summary_job (status);
