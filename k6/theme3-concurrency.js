import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        constant_request_rate: {
            executor: 'constant-arrival-rate',
            rate: 70,             // 초당 70번 좋아요 클릭 (강한 경쟁 상태 유발)
            timeUnit: '1s',
            duration: '5m',       // 5분
            preAllocatedVUs: 100,
            maxVUs: 200,
        },
    },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
    // 1번 장소에 대해 좋아요 요청
    // 경로 수정: /api/v1 삭제 -> /api/places
    const res = http.post(`${BASE_URL}/api/places/1/like`);

    check(res, { 'success rate': (r) => r.status === 200 });
}