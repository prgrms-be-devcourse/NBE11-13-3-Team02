import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import Paper from '@mui/material/Paper'
import Avatar from '@mui/material/Avatar'
import Chip from '@mui/material/Chip'
import Tabs from '@mui/material/Tabs'
import Tab from '@mui/material/Tab'
import StorefrontIcon from '@mui/icons-material/Storefront'
import PersonIcon from '@mui/icons-material/Person'
import { getMyParticipations } from '../api/participationApi'
import { getMyOrders } from '../api/orderApi'
import { getRefundStatus } from '../api/paymentApi.js'
import { getErrorMessage } from '../api/errorMessage'
import { useAuth } from '../context/AuthContext.jsx'
import LoadingScreen from '../components/LoadingScreen.jsx'
import { DELIVERY_STATUS, PARTICIPATION_STATUS, formatDateTime, formatPrice, statusMeta } from '../utils/statusMeta'

const TABS = [
  { value: 'ALL', label: '전체' },
  { value: 'PARTICIPATING', label: '참여중' },
  { value: 'CONFIRMED', label: '확정' },
  { value: 'CANCELLED', label: '취소됨' },
]

export default function MyParticipationsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [tab, setTab] = useState('ALL')
  const [participations, setParticipations] = useState([])
  const [ordersByParticipation, setOrdersByParticipation] = useState({})
  const [refundsByParticipation, setRefundsByParticipation] = useState({})
  const [stats, setStats] = useState({ total: null, confirmed: null })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    const participationRequest = tab === 'CANCELLED'
      ? Promise.all([
          getMyParticipations({ status: 'CANCELLED', page: 0, size: 50 }),
          getMyParticipations({ status: 'REFUNDED', page: 0, size: 50 }),
        ]).then(([cancelled, refunded]) => [
          ...(cancelled.data.content ?? []),
          ...(refunded.data.content ?? []),
        ].sort((a, b) => new Date(b.participatedAt) - new Date(a.participatedAt)))
      : getMyParticipations({
          status: tab === 'ALL' ? undefined : tab,
          page: 0,
          size: 50,
        }).then(({ data }) => data.content ?? [])

    return Promise.all([participationRequest, getMyOrders({ page: 0, size: 100 })])
      .then(async ([items, ordersResponse]) => {
        const orderMap = Object.fromEntries(
          (ordersResponse.data.content ?? []).map((order) => [order.participationId, order]),
        )
        const refundEntries = await Promise.all(
          items
            .filter((item) => item.status === '확정' && orderMap[item.participationId])
            .map(async (item) => {
              try {
                const { data } = await getRefundStatus(item.participationId)
                return [item.participationId, data]
              } catch (requestError) {
                if (requestError.response?.status === 404) return null
                throw requestError
              }
            }),
        )
        // 결제 승인과 주문 생성 없이 결제창만 닫은 임시 참여는 사용자 이력에서 제외한다.
        setParticipations(items.filter((item) =>
          !['취소됨', '환불됨'].includes(item.status) || orderMap[item.participationId],
        ))
        setOrdersByParticipation(orderMap)
        setRefundsByParticipation(Object.fromEntries(refundEntries.filter(Boolean)))
      })
      .catch((err) => setError(getErrorMessage(err, '참여 이력을 불러오지 못했습니다.')))
      .finally(() => setLoading(false))
  }, [tab])

  useEffect(() => {
    load()
  }, [load])

  const hasProcessingRefund = Object.values(refundsByParticipation).some((refund) =>
    ['REFUND_PENDING', 'PROCESSING'].includes(refund.status),
  )

  useEffect(() => {
    if (!hasProcessingRefund) return undefined
    const timerId = window.setInterval(load, 2000)
    return () => window.clearInterval(timerId)
  }, [hasProcessingRefund, load])

  useEffect(() => {
    Promise.all([
      getMyParticipations({ status: 'CONFIRMED', page: 0, size: 1 }),
      getMyParticipations({ page: 0, size: 100 }),
      getMyOrders({ page: 0, size: 100 }),
    ])
      .then(([confirmedRes, allRes, ordersRes]) => {
        const orderParticipationIds = new Set(
          (ordersRes.data.content ?? []).map((order) => order.participationId),
        )
        const visibleTotal = (allRes.data.content ?? []).filter((item) =>
          !['취소됨', '환불됨'].includes(item.status) || orderParticipationIds.has(item.participationId),
        ).length
        setStats({ total: visibleTotal, confirmed: confirmedRes.data.totalElements })
      })
      .catch(() => {})
  }, [])

  const goToDetail = (participation) => {
    navigate(`/my/participations/${participation.participationId}`, { state: { participation } })
  }

  return (
    <Box sx={{ maxWidth: 640, mx: 'auto' }}>
      <Paper sx={{ p: 3 }} variant="outlined">
        <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }}>
          <Avatar sx={{ width: 48, height: 48, bgcolor: 'primary.light', color: 'primary.main' }}>
            <PersonIcon />
          </Avatar>
          <Box>
            <Typography fontWeight={800}>{user?.name} 님</Typography>
            <Typography variant="body2" color="text.secondary">
              참여 {stats.total ?? '-'}회 · 성사 {stats.confirmed ?? '-'}회
            </Typography>
          </Box>
        </Stack>

        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          sx={{ minHeight: 36, mb: 1, borderBottom: '1px solid #ECEEF5' }}
        >
          {TABS.map((t) => (
            <Tab key={t.value} value={t.value} label={t.label} sx={{ minHeight: 36, fontWeight: 700 }} />
          ))}
        </Tabs>

        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}

        {loading ? (
          <LoadingScreen />
        ) : participations.length === 0 ? (
          <Typography color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
            해당 상태의 참여 내역이 없습니다.
          </Typography>
        ) : (
          <Stack spacing={1.5} sx={{ mt: 2 }}>
            {participations.map((p) => {
              const meta = statusMeta(PARTICIPATION_STATUS, p.status)
              const order = ordersByParticipation[p.participationId]
              const refund = refundsByParticipation[p.participationId]
              const deliveryMeta = order ? statusMeta(DELIVERY_STATUS, order.deliveryStatus) : null
              return (
                <Stack
                  key={p.participationId}
                  direction="row"
                  alignItems="center"
                  onClick={() => goToDetail(p)}
                  sx={{
                    p: 1.5,
                    borderRadius: 2,
                    border: '1px solid',
                    borderColor: '#ECEEF5',
                    bgcolor: 'background.paper',
                    cursor: 'pointer',
                    gap: 2,
                    '&:hover': { borderColor: 'primary.main' },
                  }}
                >
                  <Box
                    sx={{
                      width: 44,
                      height: 44,
                      borderRadius: 2,
                      bgcolor: 'background.paper',
                      border: '1px solid #ECEEF5',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <StorefrontIcon sx={{ color: 'primary.main', fontSize: 22 }} />
                  </Box>
                  <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography fontWeight={700} noWrap>
                      {p.productName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {formatDateTime(p.participatedAt)}
                    </Typography>
                    {order && (
                      <Typography variant="body2" color="text.secondary">
                        결제 {formatPrice(order.amount)}
                      </Typography>
                    )}
                  </Box>
                  <Stack spacing={0.5} alignItems="flex-end">
                    {refund && ['REFUND_PENDING', 'PROCESSING'].includes(refund.status) ? (
                      <Chip size="small" label="환불 처리중" color="warning" />
                    ) : refund && ['FAILED', 'RETRY_EXHAUSTED'].includes(refund.status) ? (
                      <Chip size="small" label="환불 실패" color="error" />
                    ) : (
                      <Chip size="small" label={meta.label} color={meta.color} />
                    )}
                    {deliveryMeta && <Chip size="small" variant="outlined" label={deliveryMeta.label} color={deliveryMeta.color} />}
                  </Stack>
                </Stack>
              )
            })}
          </Stack>
        )}
      </Paper>
    </Box>
  )
}
