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
    // 1. N+1 & 인덱스: 공유 지도 상세 조회 (Slug 기반)
    // 리팩토링 전: 연관 장소/노트 조회 시 쿼리 폭발 -> p95 급증
    const detailRes = http.get(`${BASE_URL}/api/requests/test-map-slug`);
    check(detailRes, { 'detail status is 200': (r) => r.status === 200 });

    // 2. 캐시: 정적 데이터 성격의 지하철역 목록
    // 리팩토링 전: 매번 DB I/O 발생 -> RDS CPU 부하
    const stationRes = http.get(`${BASE_URL}/api/stations`);
    check(stationRes, { 'station status is 200': (r) => r.status === 200 });

    sleep(1);
}