import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export const options = {
  scenarios: {
    concurrency_test: {
      executor: 'per-vu-iterations',
      vus: 50,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  thresholds: {
    checks: ['rate>0.95'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HMAC_SECRET =
  __ENV.HMAC_SECRET || 'local-dev-only-secret-do-not-use-in-production!!';
const SLUG = 'perf-req-001';

function issueToken(uid) {
  const expires = Math.floor(Date.now() / 1000) + 86400;
  const payload = `${uid}.${expires}`;
  const sig = crypto.hmac('sha256', HMAC_SECRET, payload, 'hex');
  return `${payload}.${sig}`;
}

export default function () {
  const uid = 101 + (__VU % 5);
  const token = issueToken(uid);

  const payload = JSON.stringify({
    items: [
      {
        externalId: 'kakao:PERF-0001',
        recommenderNickname: `conc-user-${__VU}`,
        recommendMessage: '동시성 테스트',
        guestId: `conc-vu-${__VU}-${Date.now()}`,
        tags: ['맛집'],
      },
    ],
  });

  const res = http.post(
    `${BASE_URL}/api/requests/${SLUG}/recommendations`,
    payload,
    {
      headers: { 'Content-Type': 'application/json' },
      cookies: { uid: token },
    }
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
    'docs/perf/k6/theme3-concurrency.json': JSON.stringify(data, null, 2),
  };
}
