import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Gauge } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const maxConnectionAttempts = Number(__ENV.QUEUE_MAX_CONNECTION_ATTEMPTS || 5);
const testData = new SharedArray('queue integrity test users', () => [
  JSON.parse(open('./generated/queue-load-test.json').replace(/^\uFEFF/, '')),
])[0];
const admissionLimit = Math.min(10, testData.users.length);
const expectedWaiting = testData.users.length - admissionLimit;

// 사용자 기준 지표입니다. 재시도 횟수가 아니라 최종 결과를 한 번만 집계합니다.
const admitted = new Counter('queue_admitted');
const waiting = new Counter('queue_waiting');
const unexpectedState = new Counter('queue_unexpected_state');
const attempted = new Counter('queue_attempted');
const serverReached = new Counter('queue_server_reached');
const connectionRejected = new Counter('queue_connection_rejected');
const expectedSuccess = new Counter('queue_expected_success');
const connectionRetries = new Counter('queue_connection_retries');
// Grafana가 선택값 없이도 가장 최근 실행을 찾는 기준 시각입니다.
const testStartedAt = new Gauge('queue_test_started_timestamp');

export const options = {
  scenarios: {
    queue_integrity_burst: {
      executor: 'per-vu-iterations',
      vus: testData.users.length,
      iterations: 1,
      maxDuration: '2m',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<1'],
    checks: ['rate==1'],
    queue_admitted: [`count==${admissionLimit}`],
    queue_waiting: [`count==${expectedWaiting}`],
    queue_unexpected_state: ['count==0'],
    queue_connection_rejected: ['count==0'],
  },
};

export default function () {
  const user = testData.users[__VU - 1];
  testStartedAt.add(Date.now() / 1000);
  attempted.add(1);

  let response;
  for (let attempt = 1; attempt <= maxConnectionAttempts; attempt += 1) {
    response = http.post(
      `${baseUrl}/api/group-buys/${testData.groupBuyId}/queue-token`,
      null,
      { headers: { Authorization: `Bearer ${user.accessToken}` }, tags: { endpoint: 'queue-token' } },
    );

    if (response.status !== 0) break;
    if (attempt < maxConnectionAttempts) {
      connectionRetries.add(1);
      sleep(attempt * 0.1);
    }
  }

  if (response.status === 0) {
    connectionRejected.add(1);
  } else {
    serverReached.add(1);
  }
  if (response.status === 201) expectedSuccess.add(1);

  const body = response.status === 0 || response.body === null ? {} : response.json();
  const validResponse = check(response, {
    'queue token returns 201 after retry': (res) => res.status === 201,
    'queue state is admitted or waiting': () => body.status === 'ADMITTED' || body.status === 'WAITING',
  });

  if (!validResponse) return;
  if (body.status === 'ADMITTED') admitted.add(1);
  else if (body.status === 'WAITING') waiting.add(1);
  else unexpectedState.add(1);
}
