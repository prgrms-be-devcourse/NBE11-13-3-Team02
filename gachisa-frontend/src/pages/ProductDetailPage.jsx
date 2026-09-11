import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import Chip from '@mui/material/Chip'
import Divider from '@mui/material/Divider'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogActions from '@mui/material/DialogActions'
import { useAuth } from '../context/AuthContext.jsx'
import { getErrorMessage } from '../api/errorMessage'
import { getProduct, deleteProduct, resumeProduct } from '../api/productApi'
import { fetchActiveGroupBuyIdsByProductId } from '../utils/groupBuyLookup'
import { PRODUCT_STATUS, statusMeta, formatPrice, formatDateTime } from '../utils/statusMeta'
import LoadingScreen from '../components/LoadingScreen.jsx'

export default function ProductDetailPage() {
  const { productId } = useParams()
  const { user, isSeller } = useAuth()

  const [product, setProduct] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [activeGroupBuyId, setActiveGroupBuyId] = useState(undefined)

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [resuming, setResuming] = useState(false)

  const fetchProduct = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const { data } = await getProduct(productId)
      setProduct(data)
    } catch (err) {
      setError(getErrorMessage(err, '상품 정보를 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }, [productId])

  useEffect(() => {
    fetchProduct()
  }, [fetchProduct])

  useEffect(() => {
    if (!product?.id) return
    fetchActiveGroupBuyIdsByProductId()
      .then((map) => setActiveGroupBuyId(map[product.id] ?? null))
      .catch(() => setActiveGroupBuyId(null))
  }, [product?.id])

  if (loading) return <LoadingScreen />
  if (error) {
    return (
      <Box sx={{ maxWidth: 800, mx: 'auto', p: 3 }}>
        <Alert severity="error">{error}</Alert>
      </Box>
    )
  }
  if (!product) return null

  const status = statusMeta(PRODUCT_STATUS, product.status)
  const isOwner = isSeller && user?.id === product.sellerId

  const handleSuspend = async () => {
    setDeleting(true)
    try {
      // 백엔드 DELETE /products/{id}는 실제 삭제가 아니라 판매중지(soft delete) 처리다.
      await deleteProduct(productId)
      setDeleteOpen(false)
      await fetchProduct()
    } catch (err) {
      setError(getErrorMessage(err, '판매중지 처리에 실패했습니다.'))
      setDeleteOpen(false)
    } finally {
      setDeleting(false)
    }
  }

  const handleResume = async () => {
    setResuming(true)
    try {
      await resumeProduct(productId)
      await fetchProduct()
    } catch (err) {
      setError(getErrorMessage(err, '판매 재개에 실패했습니다.'))
    } finally {
      setResuming(false)
    }
  }

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', p: 3 }}>
      <Paper sx={{ p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Typography variant="h5" fontWeight={700}>
            {product.name}
          </Typography>
          <Chip label={status.label} color={status.color} />
        </Stack>

        {activeGroupBuyId && (
          <Alert
            severity="info"
            sx={{ mt: 2, bgcolor: 'primary.light', color: 'primary.main' }}
            action={
              <Button
                component={Link}
                to={`/group-buys/${activeGroupBuyId}`}
                variant="contained"
                size="small"
                sx={{ whiteSpace: 'nowrap' }}
              >
                참여하러 가기
              </Button>
            }
          >
            이 상품은 공동구매가 진행 중이에요
          </Alert>
        )}

        {product.imageUrl ? (
          <Box
            component="img"
            src={product.imageUrl}
            alt={product.name}
            sx={{ width: '100%', maxHeight: 320, objectFit: 'cover', borderRadius: 1, my: 2 }}
          />
        ) : (
          <Box
            sx={{
              height: 200,
              bgcolor: 'grey.200',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 1,
              my: 2,
            }}
          >
            <Typography variant="body2" color="text.secondary">
              이미지 없음
            </Typography>
          </Box>
        )}

        <Stack spacing={1} sx={{ mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            카테고리: {product.categoryName ?? '-'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            판매자: {product.sellerName ?? '-'}
          </Typography>
          <Typography variant="h6" fontWeight={700}>
            {formatPrice(product.basePrice)}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            재고: {product.stock}개
          </Typography>
          <Typography variant="body2" color="text.secondary">
            등록일: {formatDateTime(product.createdAt)}
          </Typography>
        </Stack>

        <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap', mb: 2 }}>
          {product.description}
        </Typography>

        {isOwner && (
          <>
            <Divider sx={{ my: 2 }} />
            <Stack direction="row" spacing={2}>
              <Button component={Link} to={`/products/${productId}/edit`} variant="outlined">
                수정
              </Button>
              {product.status === 'SUSPENDED' ? (
                <Button variant="outlined" onClick={handleResume} disabled={resuming}>
                  판매 재개
                </Button>
              ) : (
                <Button color="error" variant="outlined" onClick={() => setDeleteOpen(true)}>
                  판매중지
                </Button>
              )}
            </Stack>
          </>
        )}
      </Paper>

      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)}>
        <DialogTitle>판매중지</DialogTitle>
        <DialogContent>
          <DialogContentText>
            이 상품 판매를 중지하시겠습니까? 판매중지 상태에서는 새로 노출/검색되지 않으며,
            나중에 "판매 재개" 버튼으로 다시 판매를 시작할 수 있습니다.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteOpen(false)}>취소</Button>
          <Button color="error" onClick={handleSuspend} disabled={deleting}>
            판매중지
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
