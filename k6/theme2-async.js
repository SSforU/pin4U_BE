import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 70,             // [Theme 1 동일] 초당 70건 요청
      timeUnit: '1s',
      duration: '5m',       // [Theme 1 동일] 5분간 지속
      preAllocatedVUs: 70,  // [Theme 1 동일] 미리 70명 대기
      maxVUs: 300,          // [Theme 1 동일] 최대 300명까지 확장
    },
  },
  thresholds: {
    // 목표: 비동기 처리이므로 훨씬 빨라야 하지만, 설정 통일을 위해 Theme 1 기준(1초) 적용
    // 실제 성공 시 p95는 50~100ms 수준으로 나와야 정상입니다.
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.01'],    // 에러율 1% 미만
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
    // RecommendationController: items가 필수값이므로 더미 데이터 전송
    const payload = JSON.stringify({
        items: [
                    {
                        // placeId 대신 external_id 등 서버가 요구하는 필드를 채워야 함
                        // (서버 로그를 보니 external_id가 required라고 뜸)
                        "placeId": "1",
                        "comment": "부하테스트",
                        "externalId": "TEST-EXT-ID-" + Math.random(), // 유니크하게 생성
                        "guestId": "3fa85f64-5717-4562-b3fc-2c963f66afa6" // UUID 형식 아무거나
                    }
                ]
    });

    const params = {
        headers: { 'Content-Type': 'application/json' }
    };

    // [경로 확인] /api/requests/{slug}/recommendations
    // AI에게 분석 요청을 보내는 API
    const res = http.post(`${BASE_URL}/api/requests/test-map-slug/recommendations`, payload, params);

    check(res, {
        'post success': (r) => r.status === 200,

        // [Theme 2 핵심 검증]
        // AI가 5초 걸리든 말든, 서버는 "알겠습니다" 하고 즉시 응답해야 함.
        // 200ms 이내에 응답이 오는지 확인 (Blocking이면 여기서 실패함)
        'is async fast': (r) => r.timings.duration < 200,
    });

    // 실패 시 로그 출력 (디버깅용)
    if (res.status !== 200 || res.timings.duration >= 200) {
        console.error(`Status: ${res.status} | Duration: ${res.timings.duration}ms | Body: ${res.body ? res.body.substring(0, 100) : ''}`);
    }
}