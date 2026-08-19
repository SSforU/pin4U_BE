import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export const options = {
  scenarios: {
    concurrency_test: {
      executor: 'per-vu-iterations',
      vus: 50,
      iterations: 1,
      maxDuration: '1m',
    },
  },
  thresholds: {
    checks: ['rate>0.95'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SLUG = __ENV.TEST_SLUG || 'test-map-slug';

export default function () {
  // POST /api/requests/{slug}/recommendations — 동시 추천
  // 각 VU가 서로 다른 guestId로 동시에 추천을 제출
  const payload = JSON.stringify({
    items: [
      {
        externalId: 'kakao:CONCURRENCY-TARGET',
        recommenderNickname: `user-${__VU}`,
        recommendMessage: '동시성 테스트',
        guestId: `vu-${__VU}-${Date.now()}`,
        tags: ['맛집'],
      },
    ],
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/api/requests/${SLUG}/recommendations`, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}

// 테스트 종료 후 DB에서 recommendedCount가 VU 수(50)와 일치하는지
// 외부에서 검증: psql -c "SELECT recommended_count FROM request_place_aggregates WHERE ..."

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-');
  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
    [`docs/perf/k6/theme3-concurrency-${ts}.json`]: JSON.stringify(data, null, 2),
  };
}
