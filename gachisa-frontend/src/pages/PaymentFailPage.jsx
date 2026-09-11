import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ANONYMOUS, loadTossPayments } from '@tosspayments/tosspayments-sdk'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import CancelIcon from '@mui/icons-material/Cancel'
import { cancelUnpaidParticipation, getPaymentByPgOrderId } from '../api/paymentApi.js'

function readSavedAttempt(pgOrderId) {
  if (!pgOrderId) return null
  try {
    return JSON.parse(sessionStorage.getItem(`payment-attempt:${pgOrderId}`) || 'null')
  } catch {
    return null
  }
}

export default function PaymentFailPage() {
  const [searchParams] = useSearchParams()
  const message = searchParams.get('message') || '결제가 취소되었거나 인증에 실패했습니다.'
  const failureCode = searchParams.get('code')
  const pgOrderId = searchParams.get('orderId')
  const [savedAttempt, setSavedAttempt] = useState(() => readSavedAttempt(pgOrderId))
  const [retrying, setRetrying] = useState(false)
  const [retryError, setRetryError] = useState('')
  const [cancelled, setCancelled] = useState(false)
  const cancellationRequestedRef = useRef(false)

  useEffect(() => {
    if (savedAttempt || !pgOrderId) return
    getPaymentByPgOrderId(pgOrderId)
      .then(({ data }) => setSavedAttempt({
        paymentId: data.paymentId,
        paymentAttemptId: data.paymentAttemptId,
        participationId: data.participationId,
        amount: data.amount,
        productName: '같이사 공동구매 상품',
        paymentMethod: data.paymentMethod,
      }))
      .catch(() => setRetryError('서버에서 결제 정보를 복구하지 못했습니다.'))
  }, [pgOrderId, savedAttempt])

  useEffect(() => {
    if (failureCode !== 'PAY_PROCESS_CANCELED'
      || !savedAttempt?.participationId
      || cancelled
      || cancellationRequestedRef.current) return
    cancellationRequestedRef.current = true
    cancelUnpaidParticipation(savedAttempt.participationId)
      .then(() => {
        sessionStorage.removeItem(`payment-attempt:${pgOrderId}`)
        setCancelled(true)
      })
      .catch(() => {
        cancellationRequestedRef.current = false
        setRetryError('취소된 참여 정보를 정리하지 못했습니다. 잠시 후 다시 시도해주세요.')
      })
  }, [failureCode, savedAttempt, cancelled, pgOrderId])

  const retryPayment = async () => {
    setRetrying(true)
    setRetryError('')
    try {
      const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY
      if (!clientKey || !savedAttempt || !pgOrderId) {
        throw new Error('재결제에 필요한 정보를 찾을 수 없습니다.')
      }
      const tossPayments = await loadTossPayments(clientKey)
      const payment = tossPayments.payment({ customerKey: ANONYMOUS })
      await payment.requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: savedAttempt.amount },
        orderId: pgOrderId,
        orderName: savedAttempt.productName,
        successUrl: `${window.location.origin}/payments/success`,
        failUrl: `${window.location.origin}/payments/fail`,
        card: { useEscrow: false, flowMode: 'DEFAULT', useCardPoint: false, useAppCardOnly: false },
      })
    } catch (error) {
      setRetryError(error.message || '결제창을 다시 열지 못했습니다.')
      setRetrying(false)
    }
  }

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', px: 2 }}>
      <Paper sx={{ p: 5, width: 420, textAlign: 'center' }} variant="outlined">
        <Stack spacing={2.5} alignItems="center">
          <CancelIcon sx={{ fontSize: 48, color: 'error.main' }} />
          <Typography variant="h6" fontWeight={800}>
            결제 실패
          </Typography>
          <Typography color="text.secondary">{message}</Typography>
          {retryError && <Typography color="error">{retryError}</Typography>}
          {savedAttempt && !cancelled && failureCode !== 'PAY_PROCESS_CANCELED' && (
            <Button onClick={retryPayment} variant="contained" fullWidth disabled={retrying}>
              {retrying ? '결제창 여는 중...' : '다시 결제하기'}
            </Button>
          )}
          {cancelled && <Typography color="text.secondary">임시 참여와 예약 인원이 취소되었습니다.</Typography>}
          <Button component={Link} to="/" variant="contained" fullWidth sx={{ mt: 1 }}>
            공동구매 목록으로
          </Button>
        </Stack>
      </Paper>
    </Box>
  )
}
