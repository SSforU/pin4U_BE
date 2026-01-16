import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '1m', target: 70 },
        { duration: '3m', target: 70 }, // 70 TPS 고정 유지
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // p95 기준 500ms 미만 목표
    },
};

const BASE_URL = 'http://16.184.53.121:8080';

export default function () {
    // 1. N+1 & 인덱스: 공유 지도 상세 조회 (컨트롤러 경로 /api/requests에 맞춤)
    const detailRes = http.get(`${BASE_URL}/api/requests/test-map-slug`);
    check(detailRes, { 'detail status is 200': (r) => r.status === 200 });

    // 2. 캐시: 지하철역 목록 (컨트롤러 경로 /api/stations에 맞춤)
    const stationRes = http.get(`${BASE_URL}/api/stations`);
    check(stationRes, { 'station status is 200': (r) => r.status === 200 });

    sleep(1);
}