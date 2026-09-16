import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const testData = new SharedArray('queue load test users', () => [
  JSON.parse(open('./generated/queue-load-test.json').replace(/^\uFEFF/, '')),
])[0];
const concurrentAdmissionLimit = Math.min(10, testData.users.length);
const expectedWaitingCount = testData.users.length - concurrentAdmissionLimit;
const correctnessOnly = __ENV.QUEUE_TEST_MODE === 'correctness';

const admitted = new Counter('queue_admitted');
const waiting = new Counter('queue_waiting');
const unexpectedState = new Counter('queue_unexpected_state');
const attempted = new Counter('queue_attempted');
const serverReached = new Counter('queue_server_reached');
const connectionRejected = new Counter('queue_connection_rejected');
const expectedSuccess = new Counter('queue_expected_success');

const thresholds = {
  http_req_failed: ['rate==0'],
  checks: ['rate==1'],
  queue_admitted: [`count==${concurrentAdmissionLimit}`],
  queue_waiting: [`count==${expectedWaitingCount}`],
  queue_unexpected_state: ['count==0'],
};

// 정합성 전용 모드에서는 성능 목표를 판정에서 제외합니다.
// 요청 성공 여부와 10명/나머지 대기 분리만 검증합니다.
if (!correctnessOnly) {
  thresholds.http_req_duration = ['p(95)<1000'];
}

export const options = {
  scenarios: {
    queue_open_burst: {
      executor: 'per-vu-iterations',
      vus: testData.users.length,
      iterations: 1,
      maxDuration: '2m',
      gracefulStop: '10s',
    },
  },
  thresholds,
};

export default function () {
  const user = testData.users[__VU - 1];
  attempted.add(1);
  const response = http.post(
    `${baseUrl}/api/group-buys/${testData.groupBuyId}/queue-token`,
    null,
    { headers: { Authorization: `Bearer ${user.accessToken}` }, tags: { endpoint: 'queue-token' } },
  );
  // 연결 자체가 거부되면 k6 응답 본문이 없을 수 있습니다.
  // 이 경우 테스트 스크립트가 예외로 멈추지 않고 실패 건수로 집계되게 합니다.
  if (response.status === 0) {
    connectionRejected.add(1);
  } else {
    serverReached.add(1);
  }
  if (response.status === 201) expectedSuccess.add(1);
  const body = response.status === 0 || response.body === null ? {} : response.json();
  const validResponse = check(response, {
    'queue token returns 201': (res) => res.status === 201,
    'queue state is admitted or waiting': () => body.status === 'ADMITTED' || body.status === 'WAITING',
  });

  if (!validResponse) return;
  if (body.status === 'ADMITTED') admitted.add(1);
  else if (body.status === 'WAITING') waiting.add(1);
  else unexpectedState.add(1);
}
