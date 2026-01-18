import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 70,
      timeUnit: '1s',
      duration: '5m', // 빠른 확인을 위해 2분
      preAllocatedVUs: 70,
      maxVUs: 300,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 목표: 1초 이내 (최적화 전엔 넘을 수 있음)
    http_req_failed: ['rate<0.01'],    // 에러율 1% 미만
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  // 1. 상세 조회 (기존 성공)
  const detailRes = http.get(`${BASE_URL}/api/requests/test-map-slug`);
  check(detailRes, { 'detail status is 200': (r) => r.status === 200 });

  // 2. 지하철역 검색 [수정 완료: Curl 성공 기준]
  // (1) 경로: /api/stations/search 로 변경
  // (2) 파라미터: name -> q 로 변경
  const targetUrl = `${BASE_URL}/api/stations/search?q=${encodeURIComponent('강남')}`;

  const stationRes = http.get(targetUrl);

  // 실패 시 로그 출력 (디버깅용)
  if (stationRes.status !== 200) {
    console.error(`Status: ${stationRes.status} | URL: ${targetUrl} | Body: ${stationRes.body.substring(0, 100)}`);
  }

  check(stationRes, {
    'station status is 200': (r) => r.status === 200
  });
}