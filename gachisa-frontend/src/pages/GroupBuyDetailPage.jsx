import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Button from '@mui/material/Button'
import LinearProgress from '@mui/material/LinearProgress'
import Alert from '@mui/material/Alert'
import Divider from '@mui/material/Divider'
import StorefrontIcon from '@mui/icons-material/Storefront'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import { getGroupBuyDetail, cancelGroupBuy } from '../api/groupBuyApi'
import { getProduct } from '../api/productApi'
import { getMyParticipations, getParticipationCount } from '../api/participationApi'
import { getErrorMessage } from '../api/errorMessage'
import { useAuth } from '../context/AuthContext.jsx'
import { useCountdown } from '../hooks/useCountdown'
import LoadingScreen from '../components/LoadingScreen.jsx'
import { GROUP_BUY_STATUS, formatPrice, statusMeta } from '../utils/statusMeta'

const formatClock = (totalSeconds) => {
  if (totalSeconds <= 0) return '00:00:00'
  const h = Math.floor(totalSeconds / 3600)
  const m = Math.floor((totalSeconds % 3600) / 60)
  const s = totalSeconds % 60
  return [h, m, s].map((v) => String(v).padStart(2, '0')).join(':')
}

export default function GroupBuyDetailPage() {
  const { groupBuyId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { isBuyer, isSeller, isAuthenticated, user } = useAuth()

  const [groupBuy, setGroupBuy] = useState(null)
  const [product, setProduct] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [cancelSubmitting, setCancelSubmitting] = useState(false)
  const [myParticipation, setMyParticipation] = useState(null)

  const { seconds } = useCountdown(groupBuy?.remainingSeconds)

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    return getGroupBuyDetail(groupBuyId)
      .then(async ({ data }) => {
        setGroupBuy(data)
        if (data.productId) {
          try {
            const { data: productData } = await getProduct(data.productId)
            setProduct(productData)
          } catch {
            setProduct(null)
          }
        }
      })
      .catch((err) => setError(getErrorMessage(err, '공동구매 정보를 불러오지 못했습니다.')))
      .finally(() => setLoading(false))
  }, [groupBuyId])

  useEffect(() => {
    load()
  }, [load])

  // Redis 기반 실시간 참여 인원 폴링 (PT-03)
  useEffect(() => {
    if (!groupBuyId || !groupBuy) return undefined
    let cancelled = false
    const tick = () => {
      getParticipationCount(groupBuyId)
        .then(({ data }) => {
          if (cancelled || !data) return
          setGroupBuy((prev) =>
            prev
              ? {
                  ...prev,
                  currentCount: data.currentCount,
                  targetCount: data.targetCount,
                  progressRate:
                    data.targetCount > 0
                      ? Math.round((data.currentCount * 1000) / data.targetCount) / 10
                      : 0,
                }
              : prev,
          )
        })
        .catch(() => {})
    }
    tick()
    const timer = setInterval(tick, 2000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [groupBuyId, groupBuy?.groupBuyId])

  useEffect(() => {
    if (!isBuyer || !groupBuy) return undefined
    let cancelled = false
    getMyParticipations({ page: 0, size: 100 })
      .then(({ data }) => {
        if (cancelled) return
        const existing = (data.content ?? []).find(
          (p) => p.groupBuyId === groupBuy.groupBuyId && (p.status === '참여중' || p.status === '확정'),
        )
        setMyParticipation(existing ?? null)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [isBuyer, groupBuy])

  const handleCancelGroupBuy = async () => {
    if (!window.confirm('이 공동구매를 취소하시겠습니까?')) return
    setCancelSubmitting(true)
    try {
      await cancelGroupBuy(groupBuyId)
      await load()
    } catch (err) {
      setError(getErrorMessage(err, '공동구매 취소에 실패했습니다.'))
    } finally {
      setCancelSubmitting(false)
    }
  }

  const handleParticipateClick = () => {
    navigate(`/group-buys/${groupBuyId}/checkout`, {
      state: { productName: groupBuy?.productName },
    })
  }

  if (loading) return <LoadingScreen />
  if (error && !groupBuy) return <Alert severity="error">{error}</Alert>
  if (!groupBuy) return null

  const meta = statusMeta(GROUP_BUY_STATUS, groupBuy.status)
  const target = groupBuy.targetCount ?? 0
  const current = groupBuy.currentCount ?? 0
  const progress = groupBuy.progressRate ?? (target > 0 ? (current / target) * 100 : 0)
  const discounted =
    product && typeof groupBuy.discountRate === 'number'
      ? Math.round(product.basePrice * (1 - groupBuy.discountRate))
      : null
  const isRecruiting = groupBuy.status === '모집중'
  const isFull = target > 0 && current >= target
  const displayLabel = isRecruiting && isFull ? '모집완료' : meta.label
  const displayColor = isRecruiting && isFull ? 'success' : meta.color
  const canParticipate = isBuyer && isRecruiting
  const needsLoginToParticipate = !isAuthenticated && isRecruiting
  const canCancel = isSeller && isRecruiting && (!product?.sellerId || product.sellerId === user?.id)

  const bullets = [
    product?.description,
    '목표 인원 도달 시 자동 주문 확정',
    '미달 시 전원 자동 환불',
  ].filter(Boolean)

  return (
    <Box>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Paper sx={{ overflow: 'hidden' }}>
        <Stack direction={{ xs: 'column', md: 'row' }}>
          <Box sx={{ width: { xs: '100%', md: 320 }, flexShrink: 0 }}>
            {product?.imageUrl ? (
              <Box
                component="img"
                src={product.imageUrl}
                alt={product.name}
                sx={{ width: '100%', height: '100%', minHeight: 280, objectFit: 'cover' }}
              />
            ) : (
              <Box
                sx={{
                  height: '100%',
                  minHeight: 280,
                  bgcolor: 'primary.light',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <StorefrontIcon sx={{ fontSize: 64, color: 'primary.main' }} />
              </Box>
            )}
          </Box>

          <Box sx={{ p: 4, flexGrow: 1 }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
              {product?.categoryName && (
                <Chip
                  size="small"
                  label={product.categoryName}
                  sx={{ bgcolor: 'primary.light', color: 'primary.main', fontWeight: 700 }}
                />
              )}
              <Chip size="small" label={displayLabel} color={displayColor} />
            </Stack>

            <Typography variant="h5" fontWeight={800} gutterBottom>
              {groupBuy.productName}
            </Typography>

            <Stack direction="row" spacing={1.5} alignItems="baseline" sx={{ mt: 1 }}>
              <Typography variant="h4" fontWeight={800}>
                {formatPrice(discounted ?? product?.basePrice)}
              </Typography>
              {product && discounted != null && (
                <Typography color="text.secondary" sx={{ textDecoration: 'line-through' }}>
                  {formatPrice(product.basePrice)}
                </Typography>
              )}
              {typeof groupBuy.discountRate === 'number' && (
                <Chip
                  size="small"
                  label={`${Math.round(groupBuy.discountRate * 100)}% 할인`}
                  sx={{ bgcolor: 'secondary.light', color: 'secondary.dark', fontWeight: 700 }}
                />
              )}
            </Stack>

            <Divider sx={{ my: 2.5 }} />

            <LinearProgress
              variant="determinate"
              value={Math.min(100, progress)}
              color={progress >= 100 ? 'success' : 'primary'}
              sx={{ height: 8, borderRadius: 4 }}
            />
            <Stack direction="row" justifyContent="space-between" sx={{ mt: 1 }}>
              <Typography variant="body2" fontWeight={700}>
                {current}/{target}명 참여
              </Typography>
              {isRecruiting && (
                <Typography variant="body2" fontWeight={700} color="secondary.dark">
                  남은시간 {formatClock(seconds ?? groupBuy.remainingSeconds ?? 0)}
                </Typography>
              )}
            </Stack>

            {bullets.length > 0 && (
              <Stack spacing={0.6} sx={{ mt: 2.5 }}>
                {bullets.map((text) => (
                  <Stack key={text} direction="row" spacing={1} alignItems="flex-start">
                    <CheckCircleIcon sx={{ fontSize: 16, color: 'primary.main', mt: 0.3 }} />
                    <Typography variant="body2" color="text.secondary">
                      {text}
                    </Typography>
                  </Stack>
                ))}
              </Stack>
            )}

            {canParticipate && (
              <Button
                variant="contained"
                color={myParticipation || isFull ? 'inherit' : 'secondary'}
                size="large"
                fullWidth
                disabled={!!myParticipation || isFull}
                onClick={handleParticipateClick}
                sx={{ mt: 3, py: 1.4 }}
              >
                {myParticipation ? '참여완료' : isFull ? '모집 완료' : '참여 신청하기'}
              </Button>
            )}

            {needsLoginToParticipate && (
              <Button
                variant="contained"
                color={isFull ? 'inherit' : 'secondary'}
                size="large"
                fullWidth
                disabled={isFull}
                sx={{ mt: 3, py: 1.4 }}
                onClick={() => navigate('/login', { state: { from: location } })}
              >
                {isFull ? '모집 완료' : '로그인하고 참여하기'}
              </Button>
            )}

            {canCancel && (
              <Button
                variant="outlined"
                color="error"
                sx={{ mt: 3 }}
                disabled={cancelSubmitting}
                onClick={handleCancelGroupBuy}
              >
                공동구매 취소
              </Button>
            )}
          </Box>
        </Stack>
      </Paper>
    </Box>
  )
}
