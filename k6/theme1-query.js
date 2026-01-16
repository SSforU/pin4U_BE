import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '1m', target: 70 },
        { duration: '3m', target: 70 },
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'],
    },
};

const BASE_URL = 'http://16.184.53.121:8080';

export default function () {
    // 1. 상세 조회 (컨트롤러 경로 /api/requests 에 맞춤)
    const detailRes = http.get(`${BASE_URL}/api/requests/test-map-slug`);
    check(detailRes, { 'detail status is 200': (r) => r.status === 200 });

    // 2. 지하철역 검색 (StationController의 /search 경로에 맞춤)
    const stationRes = http.get(`${BASE_URL}/api/stations/search?q=강남`);
    check(stationRes, { 'station status is 200': (r) => r.status === 200 });

    sleep(1);
}