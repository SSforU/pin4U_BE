import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 50,
    duration: '5m', // 5분 지속
};

const BASE_URL = 'http://16.184.53.121:8080';

export default function () {
    const payload = JSON.stringify({ placeId: 1, comment: "AI 분석 성능 측정" });
    const params = { headers: { 'Content-Type': 'application/json' } };

    // 리팩토링 전: AI 응답까지 5초 대기(Blocking) -> 스레드 풀 고갈
    // 리팩토링 후: 즉시 응답(Event 방식) -> 100ms 이내 반환
    const res = http.post(`${BASE_URL}/api/v1/recommendations`, payload, params);

    check(res, {
        'post success': (r) => r.status === 200 || r.status === 201,
        'is async fast': (r) => r.timings.duration < 200,
    });

    sleep(0.5);
}