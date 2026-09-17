import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Gauge, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const prometheusUrl = __ENV.PROMETHEUS_URL || 'http://127.0.0.1:9090';
const testType = __ENV.PAYMENT_TEST_TYPE || 'payment_smoke';
const data = new SharedArray('payment reliability users', () => [JSON.parse(open('./generated/queue-load-test.json').replace(/^\uFEFF/, ''))])[0];
const vus = testType === 'payment_concurrent' ? 10 : testType === 'payment_idempotency' || testType === 'webhook_duplicate' ? 10 : 1;

const attempted = new Counter('payment_test_attempted');
const succeeded = new Counter('payment_test_success');
const failed = new Counter('payment_test_failed');
const expected = new Counter('payment_test_expected_success');
const actual = new Counter('payment_test_actual_success');
const duplicateIgnored = new Counter('payment_test_duplicate_ignored');
const pass = new Gauge('payment_test_pass');
const started = new Gauge('payment_test_started_timestamp');
const clientP95 = new Trend('payment_test_client');

export const options = {
  scenarios: { test: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: '2m', gracefulStop: '10s' } },
  thresholds: {
    checks: ['rate==1'], http_req_failed: ['rate==0'],
    payment_test_success: [`count==${vus}`],
    payment_test_failed: ['count==0'],
    ...(testType === 'payment_idempotency' ? {
      'payment_test_actual_success{phase:idempotency}': ['count==1'],
      'payment_test_duplicate_ignored{phase:idempotency}': ['count==9'],
    } : {}),
    ...(testType === 'webhook_duplicate' ? {
      'payment_test_actual_success{phase:webhook}': ['count==1'],
      'payment_test_duplicate_ignored{phase:webhook}': ['count==9'],
    } : {}),
  },
};

function headers(user, queueToken) {
  const value = { Authorization: `Bearer ${user.accessToken}` };
  if (queueToken) { value['Queue-Token'] = queueToken; value['Idempotency-Key'] = randomUuidV4(); }
  return value;
}
function randomUuidV4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.floor(Math.random() * 16); const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
function json(response) { return response.status === 0 || !response.body ? {} : response.json(); }
function post(path, body, h, tags) {
  const r = http.post(`${baseUrl}${path}`, JSON.stringify(body), { headers: { ...h, 'Content-Type': 'application/json' }, tags });
  clientP95.add(r.timings.duration, { phase: tags.phase }); return r;
}
function phaseForTest() {
  if (testType === 'refund_smoke') return 'refund';
  if (testType === 'webhook_duplicate') return 'webhook';
  if (testType === 'payment_idempotency') return 'idempotency';
  return 'payment';
}
function metricValue(metric, result) {
  const query = `${metric}{result="${result}"}`;
  const response = http.get(`${prometheusUrl}/api/v1/query?query=${encodeURIComponent(query)}`);
  if (response.status !== 200) throw new Error(`Prometheus metric query failed: ${response.status}`);
  const values = json(response).data?.result || [];
  return values.reduce((sum, item) => sum + Number(item.value?.[1] || 0), 0);
}
function recordCount(metric, count, tags) {
  for (let i = 0; i < Math.max(0, Math.round(count)); i += 1) metric.add(1, tags);
}
function makePaid(user) {
  const queue = http.post(`${baseUrl}/api/group-buys/${data.groupBuyId}/queue-token`, null, { headers: headers(user), tags: { phase: 'payment' } });
  const q = json(queue); if (queue.status !== 201 || q.status !== 'ADMITTED') { console.error(`queue failed: ${queue.status} ${queue.body}`); return null; }
  const p = post(`/api/group-buys/${data.groupBuyId}/participations`, { quantity: 1 }, headers(user), { phase: 'payment' });
  if (p.status !== 201) { console.error(`participation failed: ${p.status} ${p.body}`); return null; }
  const participationId = json(p).result.participationId;
  const payment = post(`/api/participations/${participationId}/payment`, { paymentMethod: 'CARD' }, headers(user, q.queueToken), { phase: 'payment' });
  if (payment.status !== 201) { console.error(`payment create failed: ${payment.status} ${payment.body}`); return null; }
  const detail = json(payment);
  const paymentKey = `local-demo-${__VU}-${Date.now()}`;
  return { user, participationId, payment: detail, paymentKey, confirm: () => post(`/api/payment-attempts/${detail.paymentAttemptId}/confirm`, { paymentKey, pgOrderId: detail.pgOrderId, amount: detail.amount }, headers(user), { phase: 'payment' }) };
}

export function setup() {
  if (testType === 'payment_idempotency') {
    const paid = makePaid(data.users[0]);
    if (!paid) throw new Error('멱등성 테스트용 결제 생성에 실패했습니다.');
    return {
      user: paid.user, participationId: paid.participationId, payment: paid.payment, paymentKey: paid.paymentKey,
      confirmationBaseline: {
        success: metricValue('gachisa_payment_confirmation_total', 'success'),
        idempotent: metricValue('gachisa_payment_confirmation_total', 'idempotent'),
      },
    };
  }
  if (testType === 'webhook_duplicate') {
    const paid = makePaid(data.users[0]);
    if (!paid || paid.confirm().status !== 200) throw new Error('웹훅 테스트용 결제 승인에 실패했습니다.');
    return { user: paid.user, participationId: paid.participationId, payment: paid.payment, paymentKey: paid.paymentKey };
  }
  return null;
}
export default function(shared) {
  const phase = phaseForTest();
  started.add(Date.now() / 1000);
  pass.add(0);
  attempted.add(1, { phase });
  if (testType === 'payment_idempotency') {
    const r = post(`/api/payment-attempts/${shared.payment.paymentAttemptId}/confirm`, { paymentKey: shared.paymentKey, pgOrderId: shared.payment.pgOrderId, amount: shared.payment.amount }, headers(shared.user), { phase: 'idempotency' });
    const ok = check(r, { 'duplicate confirm returns 200': x => x.status === 200 });
    if (ok) succeeded.add(1); else failed.add(1, { phase: 'idempotency' });
    return;
  }
  if (testType === 'webhook_duplicate') {
    const r = post('/api/webhooks/toss/payments', {
      eventType: 'PAYMENT_STATUS_CHANGED', createdAt: new Date().toISOString(),
      data: { paymentKey: shared.paymentKey, orderId: shared.payment.pgOrderId, status: 'DONE' },
    }, { 'tosspayments-webhook-transmission-id': `local-webhook-${shared.paymentKey}` }, { phase: 'webhook' });
    const body = json(r); const ok = check(r, { 'webhook returns 200': x => x.status === 200 });
    if (!ok) { failed.add(1, { phase: 'webhook' }); return; }
    if (body.processed) actual.add(1, { phase: 'webhook' }); else duplicateIgnored.add(1, { phase: 'webhook' });
    succeeded.add(1); return;
  }
  expected.add(1, { phase });
  const paid = makePaid(data.users[__VU - 1]);
  if (!paid) { failed.add(1, { phase: testType === 'refund_smoke' ? 'refund' : 'payment' }); return; }
  const confirmed = paid.confirm(); const confirmedOk = check(confirmed, { 'payment confirmation returns PAID': r => r.status === 200 && json(r).paymentStatus === 'PAID' });
  if (!confirmedOk) { failed.add(1, { phase: 'payment' }); return; }
  if (testType !== 'refund_smoke') { actual.add(1, { phase: 'payment' }); succeeded.add(1); return; }
  const cancel = post(`/api/participations/${paid.participationId}/cancel`, {}, headers(paid.user), { phase: 'refund' });
  sleep(7);
  const refund = http.get(`${baseUrl}/api/participations/${paid.participationId}/refund`, { headers: headers(paid.user), tags: { phase: 'refund' } });
  const ok = check(cancel, { 'refund requested': r => r.status === 200 }) && check(refund, { 'refund completed': r => r.status === 200 && json(r).status === 'REFUNDED' });
  if (ok) { actual.add(1, { phase: 'refund' }); succeeded.add(1); } else failed.add(1, { phase: 'refund' });
}

export function teardown(shared) {
  if (testType !== 'payment_idempotency') return;
  // Prometheus가 5초 간격으로 백엔드 카운터를 수집하므로, 이번 실행의 서버 결과가 반영될 때까지 기다린다.
  sleep(6);
  const success = metricValue('gachisa_payment_confirmation_total', 'success') - shared.confirmationBaseline.success;
  const idempotent = metricValue('gachisa_payment_confirmation_total', 'idempotent') - shared.confirmationBaseline.idempotent;
  expected.add(1, { phase: 'idempotency' });
  recordCount(actual, success, { phase: 'idempotency' });
  recordCount(duplicateIgnored, idempotent, { phase: 'idempotency' });
  if (success !== 1 || idempotent !== 9) {
    failed.add(1, { phase: 'idempotency' });
    throw new Error(`멱등성 서버 검증 실패: 실제 승인=${success}, 멱등 응답=${idempotent} (기대 1/9)`);
  }
  pass.add(1);
}
