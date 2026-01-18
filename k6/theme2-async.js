import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        constant_request_rate: {
            executor: 'constant-arrival-rate',
            rate: 70,
            timeUnit: '1s',
            duration: '5m',
            preAllocatedVUs: 100,
            maxVUs: 1000,
        },
    },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
    // RecommendationController 코드 분석 결과: items가 null이면 400 에러 발생함.
    // 따라서 최소한의 dummy items를 포함해야 함.
    const payload = JSON.stringify({
        items: [
            { placeId: "1", comment: "부하테스트" } // PlaceMock이나 실제 Place 구조에 맞춤
        ]
    });
    const params = { headers: { 'Content-Type': 'application/json' } };

    // [수정됨] 경로: /api/requests/{slug}/recommendations
    const res = http.post(`${BASE_URL}/api/requests/test-map-slug/recommendations`, payload, params);

    check(res, {
        'post success': (r) => r.status === 200,
        // 비동기 처리 검증: AI 응답을 기다리지 않고 즉시 반환(200ms 미만)되는지 확인
        'is async fast': (r) => r.timings.duration < 200,
    });
}