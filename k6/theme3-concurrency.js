import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// 동시성 테스트용 설정
export const options = {
  scenarios: {
    concurrency_test: {
      executor: 'per-vu-iterations',
      vus: 50,              // 50명이 동시에
      iterations: 1,        // 딱 1번씩만 추천 (총 50회 추천)
      maxDuration: '1m',
    },
  },
};

const BASE_URL = 'http://localhost:8080/api';
// 테스트용 데이터 (미리 DB에 존재해야 함)
const REQUEST_SLUG = 'test-map-slug';
const PLACE_ID = 1; // 실제 존재하는 Place ID로 변경 필요

export default function () {
  // 1. 추천 요청 (동시 다발적 수행)
  const payload = JSON.stringify({
    placeId: PLACE_ID,
    action: 'RECOMMEND' // 가정: 추천 액션
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Cookie': `uid=${__VU}`, // 각 VU를 다른 유저로 식별
    },
  };

  // 추천 API 호출 (구현 필요)
  // 예: POST /api/requests/{slug}/places/{id}/recommend
  const res = http.post(`${BASE_URL}/requests/${REQUEST_SLUG}/places/${PLACE_ID}/recommend`, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}

// 테스트 종료 후 DB에서 `recommendedCount`가 50인지 확인하는 것은
// k6 외부(터미널)에서 `docker exec ... psql ...`로 수행