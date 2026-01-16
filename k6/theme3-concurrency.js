import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        constant_rate: {
            executor: 'constant-arrival-rate',
            rate: 70, // 70 TPS 고정
            timeUnit: '1s',
            duration: '5m',
            preAllocatedVUs: 100,
        },
    },
};

const BASE_URL = 'http://16.184.53.121:8080';

export default function () {
    // 락 부재 시: 동시에 100명이 누르면 갱신 손실 발생
    // 리팩토링 후: @Version 적용으로 정합성 100% 보장
    const res = http.post(`${BASE_URL}/api/v1/places/1/like`);

    check(res, { 'success rate': (r) => r.status === 200 });
}