import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        constant_request_rate: {
            executor: 'constant-arrival-rate',
            rate: 70,             // 목표: 초당 70회 요청 (TPS)
            timeUnit: '1s',
            duration: '5m',       // 5분간 지속
            preAllocatedVUs: 100, // 시작 VU
            maxVUs: 1000,         // [중요] 응답 지연 시 VU를 더 투입해서 70 TPS를 방어함
        },
    },
    thresholds: {
        // 로컬 환경 + 5분 지속 부하를 고려해 타임아웃 기준을 현실적으로 1초로 설정
        http_req_duration: ['p(95)<1000'],
    },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
    // 1. 상세 조회 (코드: RequestDetailController)
    const detailRes = http.get(`${BASE_URL}/api/requests/test-map-slug`);
    check(detailRes, {
        'detail status is 200': (r) => r.status === 200
    });

    // 2. 지하철역 검색 (코드: StationController)
    // [수정됨] 경로: /search, 파라미터: q
    const stationRes = http.get(`${BASE_URL}/api/stations/search?q=강남`);

    check(stationRes, {
        'station status is 200': (r) => r.status === 200
    });
}