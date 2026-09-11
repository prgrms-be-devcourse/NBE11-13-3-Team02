import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Divider from '@mui/material/Divider'
import LinearProgress from '@mui/material/LinearProgress'
import Stepper from '@mui/material/Stepper'
import Step from '@mui/material/Step'
import StepLabel from '@mui/material/StepLabel'
import Grid from '@mui/material/Grid'
import StorefrontIcon from '@mui/icons-material/Storefront'
import { getGroupBuyDetail } from '../api/groupBuyApi'
import { getProduct } from '../api/productApi'
import { cancelParticipation } from '../api/participationApi'
import { getDelivery, getMyOrderByParticipation } from '../api/orderApi'
import { getErrorMessage } from '../api/errorMessage'
import LoadingScreen from '../components/LoadingScreen.jsx'
import {
  DELIVERY_STATUS,
  PARTICIPATION_STATUS,
  formatPrice,
  formatDateTime,
  statusMeta,
} from '../utils/statusMeta'

const DELIVERY_STEPS = ['WAITING_FOR_GROUP_BUY', 'PREPARING', 'SHIPPING', 'DELIVERED']

const DELIVERY_DESCRIPTION = {
  WAITING_FOR_GROUP_BUY: '공동구매가 성공적으로 마감되면 상품 준비가 시작됩니다.',
  PREPARING: '공동구매가 마감되어 상품을 준비하고 있습니다.',
  SHIPPING: '고객님이 주문하신 상품을 자체배송 중입니다.',
  DELIVERED: '고객님이 주문하신 상품의 배송이 완료되었습니다.',
  CANCELLED: '환불이 완료되어 주문과 배송이 취소되었습니다.',
  RETURNING: '환불 완료 후 상품을 반품하고 있습니다.',
  RETURNED: '환불 및 반품이 완료되었습니다.',
}

function InfoRow({ label, value }) {
  return (
    <Stack direction="row" spacing={2}>
      <Typography variant="body2" color="text.secondary" sx={{ width: 120, flexShrink: 0 }}>
        {label}
      </Typography>
      <Typography variant="body2">{value}</Typography>
    </Stack>
  )
}

function participationFromOrder(order) {
  const refunded = ['CANCELLED', 'RETURNING', 'RETURNED'].includes(order.deliveryStatus)
  return {
    participationId: order.participationId,
    groupBuyId: order.groupBuyId,
    productName: order.productName,
    quantity: order.quantity,
    status: refunded ? '환불됨' : '확정',
    participatedAt: order.createdAt,
  }
}

export default function ParticipationDetailPage() {
  const { participationId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [participation, setParticipation] = useState(location.state?.participation ?? null)
  const [groupBuy, setGroupBuy] = useState(null)
  const [product, setProduct] = useState(null)
  const [order, setOrder] = useState(null)
  const [delivery, setDelivery] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [cancelling, setCancelling] = useState(false)

  useEffect(() => {
    let cancelled = false
    const stateParticipation = location.state?.participation ?? null

    const load = async () => {
      setLoading(true)
      setError('')
      let orderData = null
      try {
        const response = await getMyOrderByParticipation(participationId)
        orderData = response.data
        if (!cancelled) setOrder(orderData)
      } catch (requestError) {
        if (requestError.response?.status !== 404) throw requestError
      }

      const resolvedParticipation = stateParticipation ?? (orderData ? participationFromOrder(orderData) : null)
      if (!resolvedParticipation) {
        throw new Error('참여 정보를 찾을 수 없습니다. 내 참여내역에서 다시 접속해주세요.')
      }
      if (!cancelled) setParticipation(resolvedParticipation)

      const { data: groupBuyData } = await getGroupBuyDetail(resolvedParticipation.groupBuyId)
      if (cancelled) return
      setGroupBuy(groupBuyData)

      if (groupBuyData.productId) {
        const { data: productData } = await getProduct(groupBuyData.productId)
        if (!cancelled) setProduct(productData)
      }
      if (orderData?.deliveryAddressRegistered) {
        const { data: deliveryData } = await getDelivery(orderData.orderId)
        if (!cancelled) setDelivery(deliveryData)
      }
    }

    load()
      .catch((requestError) => {
        if (!cancelled) setError(getErrorMessage(requestError, requestError.message || '상세 정보를 불러오지 못했습니다.'))
      })
      .finally(() => !cancelled && setLoading(false))

    return () => {
      cancelled = true
    }
  }, [participationId])

  const handleCancel = async () => {
    const groupBuyOpen = groupBuy?.status === '모집중' && new Date(groupBuy.deadline) > new Date()
    const message = groupBuyOpen
      ? '공동구매 참여를 취소하시겠습니까? 결제된 금액이 있다면 전액 환불됩니다.'
      : '마감된 공동구매입니다. 결제 금액의 환불을 요청하시겠습니까?'
    if (!window.confirm(message)) return
    setCancelling(true)
    setError('')
    try {
      const { data } = await cancelParticipation(participationId)
      if (data.refund) {
        navigate(`/my/participations/${participationId}/refund`, { replace: true })
      } else {
        navigate('/my/participations', { replace: true })
      }
    } catch (requestError) {
      setError(getErrorMessage(requestError, '참여 취소에 실패했습니다.'))
    } finally {
      setCancelling(false)
    }
  }

  if (loading) return <LoadingScreen />

  if (error && !participation) {
    return (
      <Box>
        <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>
        <Button component={Link} to="/my/participations">내 참여내역으로</Button>
      </Box>
    )
  }

  if (!participation) return null

  const participationMeta = statusMeta(PARTICIPATION_STATUS, participation.status)
  const groupBuyOpen = groupBuy?.status === '모집중' && new Date(groupBuy.deadline) > new Date()
  const canCancelBeforeDeadline = participation.status === '참여중' && groupBuyOpen
  const canRequestRefund = participation.status === '확정'
  const canTakeAction = canCancelBeforeDeadline || canRequestRefund
  const target = groupBuy?.targetCount ?? 0
  const current = groupBuy?.currentCount ?? 0
  const progress = groupBuy?.progressRate ?? (target > 0 ? (current / target) * 100 : 0)
  const daysLeft = groupBuy ? Math.ceil((new Date(groupBuy.deadline) - Date.now()) / 86400000) : null
  const calculatedAmount = product && groupBuy && typeof groupBuy.discountRate === 'number'
    ? Math.round(product.basePrice * (1 - groupBuy.discountRate)) * participation.quantity
    : null
  const amount = order?.amount ?? calculatedAmount
  const deliveryStatus = order?.deliveryStatus
  const deliveryMeta = deliveryStatus ? statusMeta(DELIVERY_STATUS, deliveryStatus) : null
  const activeStep = deliveryStatus ? DELIVERY_STEPS.indexOf(deliveryStatus) : -1
  const deliveryStopped = ['CANCELLED', 'RETURNING', 'RETURNED'].includes(deliveryStatus)

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>참여·주문 상세</Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {order && !deliveryStopped && (
        <Paper sx={{ p: 3, mb: 3 }} elevation={1}>
          <Stepper activeStep={activeStep} alternativeLabel>
            {DELIVERY_STEPS.map((step) => (
              <Step key={step} completed={DELIVERY_STEPS.indexOf(step) <= activeStep}>
                <StepLabel>{statusMeta(DELIVERY_STATUS, step).label}</StepLabel>
              </Step>
            ))}
          </Stepper>
        </Paper>
      )}

      {deliveryStopped && (
        <Alert severity={deliveryStatus === 'CANCELLED' ? 'info' : 'warning'} sx={{ mb: 3 }}>
          {DELIVERY_DESCRIPTION[deliveryStatus]}
        </Alert>
      )}

      <Paper sx={{ p: 3 }} elevation={1}>
        <Stack spacing={1.5}>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
            <Stack direction="row" spacing={2} alignItems="center">
              {order?.productImageUrl ? (
                <Box component="img" src={order.productImageUrl} alt={participation.productName}
                  sx={{ width: 100, height: 100, objectFit: 'cover', borderRadius: 2 }} />
              ) : (
                <Box sx={{ width: 72, height: 72, borderRadius: 2, bgcolor: 'primary.light', display: 'grid', placeItems: 'center' }}>
                  <StorefrontIcon sx={{ color: 'primary.main' }} />
                </Box>
              )}
              <Box>
                <Typography variant="h6" fontWeight={800}>{participation.productName}</Typography>
                <Typography color="text.secondary">수량 {participation.quantity}개</Typography>
                <Typography fontWeight={700}>{formatPrice(amount)}</Typography>
              </Box>
            </Stack>
            <Chip size="small" label={participationMeta.label} color={participationMeta.color} />
          </Stack>

          {groupBuy && (
            <Box sx={{ pt: 1 }}>
              <LinearProgress variant="determinate" value={Math.min(100, progress)}
                color={progress >= 100 ? 'success' : 'primary'} sx={{ height: 8, borderRadius: 4 }} />
              <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.5 }}>
                <Typography variant="body2" fontWeight={700}>{current}/{target}명 참여</Typography>
                {groupBuy.status === '모집중' && (
                  <Typography variant="body2" fontWeight={700} color="secondary.dark">
                    마감까지 {daysLeft > 0 ? `D-${daysLeft}` : '마감임박'}
                  </Typography>
                )}
              </Stack>
            </Box>
          )}

          <Divider />
          {order && <InfoRow label="주문번호" value={order.orderNumber} />}
          <InfoRow label="참여 상태" value={<Chip size="small" label={participationMeta.label} color={participationMeta.color} />} />
          {deliveryMeta && <InfoRow label="배송 상태" value={<Chip size="small" label={deliveryMeta.label} color={deliveryMeta.color} />} />}
          <Divider />
          <InfoRow label="참여 신청일" value={formatDateTime(participation.participatedAt)} />
          {order && <InfoRow label="주문일시" value={formatDateTime(order.createdAt)} />}
        </Stack>
      </Paper>

      {delivery && (
        <Paper sx={{ p: 3, mt: 3 }} elevation={1}>
          <Typography variant="h6" fontWeight={800} gutterBottom>배송 정보</Typography>
          <Typography color="text.secondary" sx={{ mb: 2 }}>{DELIVERY_DESCRIPTION[delivery.deliveryStatus]}</Typography>
          <Divider sx={{ mb: 2 }} />
          <Grid container spacing={3}>
            <Grid item xs={12} sm={6}>
              <Stack spacing={0.5}>
                <Typography fontWeight={700}>{delivery.carrier}</Typography>
                <Typography variant="body2" color="text.secondary">송장번호: {delivery.trackingNumber ?? '없음'}</Typography>
                {delivery.expectedDeliveryAt && (
                  <Typography variant="body2" color="text.secondary">배송 예정: {formatDateTime(delivery.expectedDeliveryAt)}</Typography>
                )}
              </Stack>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Stack spacing={0.5}>
                <Typography variant="body2">받는 사람: {delivery.recipientName ?? '-'}</Typography>
                <Typography variant="body2">받는 주소: {delivery.address ?? '-'} {delivery.addressDetail ?? ''}</Typography>
                <Typography variant="body2">배송 요청사항: {delivery.deliveryRequest || '없음'}</Typography>
              </Stack>
            </Grid>
          </Grid>
        </Paper>
      )}

      {canTakeAction && (
        <Paper sx={{ p: 3, mt: 3 }} elevation={1}>
          <Alert severity="info" icon={false} sx={{ bgcolor: 'grey.100', color: 'text.secondary', mb: 2 }}>
            {groupBuyOpen
              ? '마감 전 참여 취소가 완료되면 참여 인원이 다시 차감됩니다.'
              : '공동구매 마감 후에는 참여 취소가 아닌 결제 환불로 처리됩니다.'}
          </Alert>
          <Button variant="outlined" color="error" fullWidth size="large" disabled={cancelling} onClick={handleCancel}>
            {cancelling ? '처리 중...' : groupBuyOpen ? '참여 취소하기' : '환불 요청하기'}
          </Button>
        </Paper>
      )}

      <Stack direction="row" spacing={2} sx={{ mt: 3 }}>
        {order && !order.deliveryAddressRegistered && !deliveryStopped && (
          <Button component={Link} to={`/orders/${order.orderId}/delivery-address`} variant="contained">배송지 입력</Button>
        )}
        <Button component={Link} to="/my/participations">내 참여내역으로</Button>
      </Stack>
    </Box>
  )
}
