import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import MenuItem from '@mui/material/MenuItem'
import { getProducts } from '../api/productApi'
import { createGroupBuy } from '../api/groupBuyApi'
import { getErrorMessage } from '../api/errorMessage'
import { useAuth } from '../context/AuthContext.jsx'
import LoadingScreen from '../components/LoadingScreen.jsx'

export default function GroupBuyCreatePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const [form, setForm] = useState({
    productId: '',
    targetCount: 10,
    discountRatePercent: 10,
    openAt: '',
    deadline: '',
  })

  useEffect(() => {
    getProducts()
      .then((res) => {
        const mine = res.data.filter((p) => p.sellerId === user?.id && p.status === 'ON_SALE')
        setProducts(mine)
      })
      .catch((err) => setError(getErrorMessage(err, '상품 목록을 불러오지 못했습니다.')))
      .finally(() => setLoading(false))
  }, [user])

  const selectedProduct = products.find((p) => p.id === Number(form.productId))

  const handleChange = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const { data } = await createGroupBuy({
        productId: Number(form.productId),
        targetCount: Number(form.targetCount),
        discountRate: Number(form.discountRatePercent) / 100,
        openAt: form.openAt ? new Date(form.openAt).toISOString() : null,
        deadline: form.deadline ? new Date(form.deadline).toISOString() : null,
      })
      navigate(data?.groupBuyId ? `/group-buys/${data.groupBuyId}` : '/')
    } catch (err) {
      setError(getErrorMessage(err, '공동구매 등록에 실패했습니다.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <LoadingScreen />

  return (
    <Box sx={{ maxWidth: 520, mx: 'auto' }}>
      <Typography variant="h5" fontWeight={700} gutterBottom>
        공동구매 등록
      </Typography>
      <Paper sx={{ p: 3, mt: 2 }}>
        {products.length === 0 && !error && (
          <Alert severity="info" sx={{ mb: 2 }}>
            판매중인 상품이 없습니다. 먼저 상품을 등록해주세요.
          </Alert>
        )}
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              select
              label="상품 선택"
              value={form.productId}
              onChange={handleChange('productId')}
              required
              fullWidth
            >
              {products.map((p) => (
                <MenuItem key={p.id} value={p.id}>
                  {p.name} (재고 {p.stock})
                </MenuItem>
              ))}
            </TextField>

            <TextField
              type="number"
              label="목표 인원"
              value={form.targetCount}
              onChange={handleChange('targetCount')}
              inputProps={{ min: 1 }}
              required
              fullWidth
            />
            <TextField
              type="number"
              label="할인율 (%)"
              value={form.discountRatePercent}
              onChange={handleChange('discountRatePercent')}
              inputProps={{ min: 0, max: 100 }}
              required
              fullWidth
            />
            <TextField
              type="datetime-local"
              label="시작 일시"
              value={form.openAt}
              onChange={handleChange('openAt')}
              InputLabelProps={{ shrink: true }}
              required
              fullWidth
            />
            <TextField
              type="datetime-local"
              label="마감 일시"
              value={form.deadline}
              onChange={handleChange('deadline')}
              InputLabelProps={{ shrink: true }}
              required
              fullWidth
            />
            <Button type="submit" variant="contained" size="large" disabled={submitting || !selectedProduct}>
              {submitting ? '등록 중...' : '공동구매 등록'}
            </Button>
          </Stack>
        </Box>
      </Paper>
    </Box>
  )
}
