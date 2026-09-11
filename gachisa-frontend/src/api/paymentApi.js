import axiosInstance from './axiosInstance.js'

export const issueQueueToken = (groupBuyId) =>
  axiosInstance.post(`/group-buys/${groupBuyId}/queue-token`)

export const getQueueStatus = (groupBuyId, queueToken) =>
  axiosInstance.get(`/group-buys/${groupBuyId}/queue-token/${queueToken}/status`)

export const createPayment = (
  participationId,
  paymentMethod,
  idempotencyKey,
  queueToken,
) =>
  axiosInstance.post(
    `/participations/${participationId}/payment`,
    { paymentMethod },
    {
      headers: {
        'Idempotency-Key': idempotencyKey,
        'Queue-Token': queueToken,
      },
    },
  )

export const confirmPayment = (paymentAttemptId, paymentKey, pgOrderId, amount) =>
  axiosInstance.post(`/payment-attempts/${paymentAttemptId}/confirm`, {
    paymentKey,
    pgOrderId,
    amount,
  })

export const confirmPaymentByPgOrderId = (paymentKey, pgOrderId, amount) =>
  axiosInstance.post('/payments/confirm', { paymentKey, pgOrderId, amount })

export const getPayment = (paymentId) => axiosInstance.get(`/payments/${paymentId}`)

export const getPaymentByPgOrderId = (pgOrderId) =>
  axiosInstance.get(`/payments/pg-orders/${pgOrderId}`)

export const cancelUnpaidParticipation = (participationId) =>
  axiosInstance.post(`/participations/${participationId}/cancel`)

export const getRefundStatus = (participationId) =>
  axiosInstance.get(`/participations/${participationId}/refund`)
