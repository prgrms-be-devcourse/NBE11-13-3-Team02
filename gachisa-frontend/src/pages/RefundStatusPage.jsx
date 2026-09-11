import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { getRefundStatus } from '../api/paymentApi.js'
import { formatDateTime, formatPrice } from '../utils/statusMeta.js'

const FINAL_STATUSES = new Set(['REFUNDED', 'FAILED', 'RETRY_EXHAUSTED'])
const STATUS_TEXT = {
  REFUND_PENDING: '환불 요청을 접수했습니다.',
  PROCESSING: '토스페이먼츠에서 환불을 처리하고 있습니다.',
  REFUNDED: '환불이 완료되었습니다.',
  FAILED: '환불 처리에 실패했습니다.',
  RETRY_EXHAUSTED: '자동 환불 재시도 횟수를 초과했습니다.',
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

export default function RefundStatusPage() {
  const { participationId } = useParams()
  const [refund, setRefund] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      for (let count = 0; count < 30 && !cancelled; count += 1) {
        try {
          const { data } = await getRefundStatus(participationId)
          if (cancelled) return
          setRefund(data)
          if (FINAL_STATUSES.has(data.status)) return
        } catch (requestError) {
          if (!cancelled) {
            setError(requestError.response?.data?.message ?? '환불 상태를 확인하지 못했습니다.')
          }
          return
        }
        await wait(2000)
      }
    }
    load()
    return () => { cancelled = true }
  }, [participationId])

  const processing = refund && !FINAL_STATUSES.has(refund.status)

  return (
    <Box sx={{ maxWidth: 480, mx: 'auto' }}>
      <Paper sx={{ p: 4 }}>
        <Stack spacing={2.5} alignItems="center">
          {processing && <CircularProgress />}
          <Typography variant="h5" fontWeight={800}>환불 상태</Typography>
          {error && <Alert severity="error" sx={{ width: '100%' }}>{error}</Alert>}
          {refund && (
            <>
              <Alert severity={refund.status === 'REFUNDED' ? 'success' : processing ? 'info' : 'error'}
                sx={{ width: '100%' }}>
                {STATUS_TEXT[refund.status] ?? refund.status}
              </Alert>
              <Typography>환불 금액: {formatPrice(refund.amount)}</Typography>
              <Typography color="text.secondary">요청 시각: {formatDateTime(refund.requestedAt)}</Typography>
              {refund.refundedAt && (
                <Typography color="text.secondary">완료 시각: {formatDateTime(refund.refundedAt)}</Typography>
              )}
              {refund.failureMessage && <Typography color="error">{refund.failureMessage}</Typography>}
            </>
          )}
          <Button component={Link} to="/my/participations">내 참여 이력으로</Button>
        </Stack>
      </Paper>
    </Box>
  )
}
