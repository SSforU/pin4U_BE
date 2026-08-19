import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 70,
      maxVUs: 500,
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
  // GET /api/requests/{slug} — 핀 상세 조회
  const detailRes = http.get(`${BASE_URL}/api/requests/${SLUG}`);
  check(detailRes, {
    'detail status is 200': (r) => r.status === 200,
  });

  // GET /api/stations/search?q=강남 — 역 검색
  const stationRes = http.get(`${BASE_URL}/api/stations/search?q=${encodeURIComponent('강남')}`);
  check(stationRes, {
    'station status is 200': (r) => r.status === 200,
  });
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-');
  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
    [`docs/perf/k6/theme1-query-${ts}.json`]: JSON.stringify(data, null, 2),
  };
}
