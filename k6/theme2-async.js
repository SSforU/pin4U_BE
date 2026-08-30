import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export const options = {
  scenarios: {
    async_load: {
      executor: 'constant-arrival-rate',
      rate: 5,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 30,
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HMAC_SECRET =
  __ENV.HMAC_SECRET || 'local-dev-only-secret-do-not-use-in-production!!';
const SLUGS = Array.from({ length: 50 }, (_, i) =>
  'perf-req-' + String(i + 1).padStart(3, '0')
);

function issueToken(uid) {
  const expires = Math.floor(Date.now() / 1000) + 86400;
  const payload = `${uid}.${expires}`;
  const sig = crypto.hmac('sha256', HMAC_SECRET, payload, 'hex');
  return `${payload}.${sig}`;
}

export default function () {
  const slug = SLUGS[Math.floor(Math.random() * SLUGS.length)];
  const uid = 101 + Math.floor(Math.random() * 5);
  const token = issueToken(uid);

  const payload = JSON.stringify({
    items: [
      {
        externalId: 'kakao:PERF-' + String(Math.floor(Math.random() * 500) + 1).padStart(4, '0'),
        recommenderNickname: `k6user-${uid}`,
        recommendMessage: 'k6 비동기 부하 테스트',
        guestId: `k6-${__VU}-${Date.now()}`,
        tags: ['맛집'],
      },
    ],
  });

  const res = http.post(`${BASE_URL}/api/requests/${slug}/recommendations`, payload, {
    headers: { 'Content-Type': 'application/json' },
    cookies: { uid: token },
  });

  check(res, {
    'post status is 200': (r) => r.status === 200,
  });
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
    'docs/perf/k6/theme2-async.json': JSON.stringify(data, null, 2),
  };
}
