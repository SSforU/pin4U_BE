import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 70,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 70,
      maxVUs: 300,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.95'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SLUG = __ENV.TEST_SLUG || 'test-map-slug';

export default function () {
  // POST /api/requests/{slug}/recommendations — 추천 제출
  // 이벤트 기반 비동기 처리이므로 서버는 즉시 응답해야 함
  const payload = JSON.stringify({
    items: [
      {
        externalId: 'kakao:TEST-' + Math.floor(Math.random() * 10000),
        recommenderNickname: 'k6tester',
        recommendMessage: '부하테스트 추천',
        guestId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        tags: ['맛집'],
      },
    ],
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/api/requests/${SLUG}/recommendations`, payload, params);

  check(res, {
    'post status is 200': (r) => r.status === 200,
    'is async fast (< 300ms)': (r) => r.timings.duration < 300,
  });
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-');
  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
    [`docs/perf/k6/theme2-async-${ts}.json`]: JSON.stringify(data, null, 2),
  };
}
