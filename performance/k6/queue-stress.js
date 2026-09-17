import http from 'k6/http';
import { sleep } from 'k6';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const maxVus = Number(__ENV.MAX_VUS || 500);
const rampDuration = __ENV.RAMP_DURATION || '10s';
const holdDuration = __ENV.HOLD_DURATION || '20s';

const testData = new SharedArray('queue stress users', () => [
  JSON.parse(open('./generated/queue-load-test.json').replace(/^\uFEFF/, '')),
])[0];

// 500명 목표 부하 이후에도 같은 API의 기술적 한계를 탐색할 수 있게 단계적으로 증가시킵니다.
const standardLevels = [10, 50, 100, 200, 500, 700, 1000, 1500, 2000];
const levels = standardLevels.filter((level) => level <= maxVus);
if (levels.at(-1) !== maxVus) levels.push(maxVus);

const stages = levels.flatMap((level) => [
  { duration: rampDuration, target: level },
  { duration: holdDuration, target: level },
]);
stages.push({ duration: rampDuration, target: 0 });

export const options = {
  scenarios: {
    queue_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages,
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // 기준을 넘으면 k6는 실패 코드로 끝나지만, 모든 단계는 끝까지 실행해 한계 구간을 남깁니다.
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  // 준비된 계정을 VU에 순환 배정합니다. 이 테스트는 고유 사용자 수가 아니라 API 동시 처리 한계를 측정합니다.
  const user = testData.users[(__VU - 1) % testData.users.length];
  const response = http.post(
    `${baseUrl}/api/group-buys/${testData.groupBuyId}/queue-token`,
    null,
    {
      headers: { Authorization: `Bearer ${user.accessToken}` },
      tags: { test_type: 'queue_stress' },
    },
  );

  check(response, {
    'queue token returns 201': (res) => res.status === 201,
  });

  // VU 수가 곧 초당 요청 수와 비슷한 의미를 갖도록 무한 반복 폭주를 막습니다.
  sleep(1);
}
