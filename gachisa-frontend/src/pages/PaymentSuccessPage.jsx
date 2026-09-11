import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import {
  confirmPayment,
  confirmPaymentByPgOrderId,
  getPayment,
  getPaymentByPgOrderId,
} from '../api/paymentApi.js'
import { useAuth } from '../context/AuthContext.jsx'

const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))
const responseData = (response) => response.data?.data ?? response.data
const confirmationRequests = new Map()

async function waitForFinalPayment(paymentId) {
  // 백엔드 복구 스케줄러(기본 60초)가 한 번 이상 실행될 시간을 확보한다.
  for (let count = 0; count < 45; count += 1) {
    const payment = responseData(await getPayment(paymentId))
    if (payment.paymentStatus === 'PAID' || payment.attemptStatus !== 'PROCESSING') {
      return payment
    }
    await wait(2000)
  }
  return null
}

export default function PaymentSuccessPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { initializing, isAuthenticated } = useAuth()
  const [message, setMessage] = useState('결제를 승인하고 있습니다.')

  useEffect(() => {
    if (initializing) {
      setMessage('로그인 정보를 확인하고 있습니다.')
      return
    }
    if (!isAuthenticated) {
      setMessage('로그인 정보를 확인할 수 없습니다. 다시 로그인해주세요.')
      return
    }

    const paymentKey = searchParams.get('paymentKey')
    const pgOrderId = searchParams.get('orderId')
    const amount = Number(searchParams.get('amount'))
    const savedAttempt = sessionStorage.getItem(`payment-attempt:${pgOrderId}`)

    if (!paymentKey || !pgOrderId || !amount) {
      setMessage('결제 승인에 필요한 정보가 없습니다.')
      return
    }

    const paymentAttempt = savedAttempt ? JSON.parse(savedAttempt) : null
    const confirmationKey = paymentAttempt?.paymentAttemptId ?? pgOrderId
    let confirmationRequest = confirmationRequests.get(confirmationKey)
    if (!confirmationRequest) {
      confirmationRequest = paymentAttempt
        ? confirmPayment(paymentAttempt.paymentAttemptId, paymentKey, pgOrderId, amount)
        : confirmPaymentByPgOrderId(paymentKey, pgOrderId, amount)
      confirmationRequests.set(confirmationKey, confirmationRequest)
    }

    confirmationRequest
      .then(async (response) => {
        let payment = responseData(response)
        if (payment.attemptStatus === 'PROCESSING') {
          payment = await waitForFinalPayment(payment.paymentId)
        }

        if (payment?.paymentStatus === 'PAID' && payment.orderId) {
          sessionStorage.removeItem(`payment-attempt:${pgOrderId}`)
          navigate(`/orders/${payment.orderId}/delivery-address`, { replace: true })
          return
        }
        setMessage('결제 결과를 확인하고 있습니다. 잠시 후 다시 시도해주세요.')
      })
      .catch(async (error) => {
        // 승인 요청 응답이 유실돼도 PG 또는 복구 스케줄러가 결제를 완료했을 수 있다.
        try {
          const current = paymentAttempt
            ? responseData(await getPayment(paymentAttempt.paymentId))
            : responseData(await getPaymentByPgOrderId(pgOrderId))
          const payment = current.attemptStatus === 'PROCESSING'
            ? await waitForFinalPayment(current.paymentId)
            : current
          if (payment?.paymentStatus === 'PAID' && payment.orderId) {
            sessionStorage.removeItem(`payment-attempt:${pgOrderId}`)
            navigate(`/orders/${payment.orderId}/delivery-address`, { replace: true })
            return
          }
        } catch {
          // 최종 사용자 메시지는 최초 승인 요청 오류를 기준으로 표시한다.
        }
        setMessage(error.response?.data?.message ?? '결제 승인에 실패했습니다.')
      })
  }, [initializing, isAuthenticated, navigate, searchParams])

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', px: 2 }}>
      <Paper sx={{ p: 5, width: 420, textAlign: 'center' }} variant="outlined">
        <Stack spacing={2.5} alignItems="center">
          <CircularProgress color="primary" />
          <Typography variant="h6" fontWeight={800}>
            결제 확인 중
          </Typography>
          <Typography color="text.secondary">{message}</Typography>
        </Stack>
      </Paper>
    </Box>
  )
}
