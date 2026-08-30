import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export const options = {
  scenarios: {
    query_load: {
      executor: 'constant-arrival-rate',
      rate: 10,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 20,
      maxVUs: 50,
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SLUGS = Array.from({ length: 50 }, (_, i) =>
  'perf-req-' + String(i + 1).padStart(3, '0')
);

export default function () {
  const slug = SLUGS[Math.floor(Math.random() * SLUGS.length)];

  const detailRes = http.get(`${BASE_URL}/api/requests/${slug}`);
  check(detailRes, {
    'detail status is 200': (r) => r.status === 200,
  });

  const stationRes = http.get(
    `${BASE_URL}/api/stations/search?q=${encodeURIComponent('강남')}`
  );
  check(stationRes, {
    'station status is 200': (r) => r.status === 200,
  });
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
    'docs/perf/k6/theme1-query.json': JSON.stringify(data, null, 2),
  };
}
