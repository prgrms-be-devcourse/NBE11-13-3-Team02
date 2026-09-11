import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import Grid from '@mui/material/Grid'
import Card from '@mui/material/Card'
import CardActionArea from '@mui/material/CardActionArea'
import CardContent from '@mui/material/CardContent'
import CardMedia from '@mui/material/CardMedia'
import Chip from '@mui/material/Chip'
import Collapse from '@mui/material/Collapse'
import TuneIcon from '@mui/icons-material/Tune'
import { getMyProducts, searchMyProducts } from '../api/productApi'
import { getCategories } from '../api/categoryApi'
import { getErrorMessage } from '../api/errorMessage'
import { PRODUCT_STATUS, statusMeta, formatPrice } from '../utils/statusMeta'
import LoadingScreen from '../components/LoadingScreen.jsx'
import CategoryTreeSelect from '../components/CategoryTreeSelect.jsx'

const emptyFilters = { keyword: '', categoryId: '', minPrice: '', maxPrice: '' }

export default function MyProductsPage() {
  const [products, setProducts] = useState([])
  const [categories, setCategories] = useState([])
  const [filters, setFilters] = useState(emptyFilters)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filterError, setFilterError] = useState('')
  const [filterOpen, setFilterOpen] = useState(false)

  useEffect(() => {
    getCategories()
      .then(({ data }) => setCategories(data ?? []))
      .catch(() => setCategories([]))
  }, [])

  const fetchProducts = useCallback(async (currentFilters) => {
    setLoading(true)
    setError('')
    try {
      const hasFilter = Object.values(currentFilters).some((value) => value !== '' && value !== undefined)
      const { data } = hasFilter
        ? await searchMyProducts({
            keyword: currentFilters.keyword || undefined,
            categoryId: currentFilters.categoryId || undefined,
            minPrice: currentFilters.minPrice === '' ? undefined : Number(currentFilters.minPrice),
            maxPrice: currentFilters.maxPrice === '' ? undefined : Number(currentFilters.maxPrice),
          })
        : await getMyProducts()
      setProducts(data)
    } catch (err) {
      setError(getErrorMessage(err, '내 상품 목록을 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchProducts(emptyFilters)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetchProducts])

  const handleChange = (field) => (e) => setFilters((prev) => ({ ...prev, [field]: e.target.value }))

  // 가격 필드는 음수 입력 자체를 막는다 (타이핑/붙여넣기 모두 무시)
  const handlePriceChange = (field) => (e) => {
    const value = e.target.value
    if (value !== '' && Number(value) < 0) return
    setFilters((prev) => ({ ...prev, [field]: value }))
  }

  const handleSearch = (e) => {
    e.preventDefault()
    setFilterError('')

    const min = filters.minPrice === '' ? null : Number(filters.minPrice)
    const max = filters.maxPrice === '' ? null : Number(filters.maxPrice)

    if ((min !== null && min < 0) || (max !== null && max < 0)) {
      setFilterError('가격은 0원 이상으로 입력해주세요.')
      return
    }
    if (min !== null && max !== null && min > max) {
      setFilterError('최소 가격이 최대 가격보다 클 수 없습니다.')
      return
    }

    fetchProducts(filters)
  }

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', p: 3 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>
          내 상품 관리
        </Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          <Button
            size="small"
            startIcon={<TuneIcon fontSize="small" />}
            onClick={() => setFilterOpen((v) => !v)}
            sx={{ color: filterOpen ? 'primary.main' : 'text.secondary', whiteSpace: 'nowrap' }}
          >
            상세검색
          </Button>
          <Button component={Link} to="/products/new" variant="contained">
            상품 등록
          </Button>
        </Stack>
      </Stack>

      <Collapse in={filterOpen}>
        <Paper component="form" onSubmit={handleSearch} sx={{ p: 2, mb: 3 }}>
          <Stack direction="row" flexWrap="wrap" useFlexGap spacing={2} alignItems="center">
            <TextField
              label="키워드"
              value={filters.keyword}
              onChange={handleChange('keyword')}
              size="small"
              sx={{ flex: '1 1 200px' }}
            />
            <CategoryTreeSelect
              categories={categories}
              value={filters.categoryId}
              onChange={(id) => setFilters((prev) => ({ ...prev, categoryId: id }))}
            />
            <TextField
              label="최소 가격"
              type="number"
              value={filters.minPrice}
              onChange={handlePriceChange('minPrice')}
              inputProps={{ min: 0 }}
              size="small"
              sx={{ width: 160, flexShrink: 0 }}
            />
            <TextField
              label="최대 가격"
              type="number"
              value={filters.maxPrice}
              onChange={handlePriceChange('maxPrice')}
              inputProps={{ min: 0 }}
              size="small"
              sx={{ width: 160, flexShrink: 0 }}
            />
            <Button type="submit" variant="contained" sx={{ whiteSpace: 'nowrap', flexShrink: 0 }}>
              검색
            </Button>
          </Stack>
          {filterError && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              {filterError}
            </Alert>
          )}
        </Paper>
      </Collapse>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {loading ? (
        <LoadingScreen />
      ) : products.length === 0 ? (
        <Typography color="text.secondary" textAlign="center" sx={{ py: 6 }}>
          조건에 맞는 상품이 없습니다.
        </Typography>
      ) : (
        <Grid container spacing={2}>
          {products.map((product) => {
            const status = statusMeta(PRODUCT_STATUS, product.status)
            return (
              <Grid item xs={12} sm={6} md={4} lg={3} key={product.id}>
                <Card variant="outlined">
                  <CardActionArea component={Link} to={`/products/${product.id}`}>
                    {product.imageUrl ? (
                      <CardMedia component="img" height={160} image={product.imageUrl} alt={product.name} />
                    ) : (
                      <Box
                        sx={{
                          height: 160,
                          bgcolor: 'grey.200',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Typography variant="body2" color="text.secondary">
                          이미지 없음
                        </Typography>
                      </Box>
                    )}
                    <CardContent>
                      <Typography variant="subtitle1" fontWeight={600} noWrap>
                        {product.name}
                      </Typography>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {product.categoryName ?? '-'}
                      </Typography>
                      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mt: 1 }}>
                        <Typography variant="subtitle2" fontWeight={700}>
                          {formatPrice(product.basePrice)}
                        </Typography>
                        <Chip label={status.label} color={status.color} size="small" />
                      </Stack>
                      <Typography variant="caption" color="text.secondary">
                        재고 {product.stock}개
                      </Typography>
                    </CardContent>
                  </CardActionArea>
                </Card>
              </Grid>
            )
          })}
        </Grid>
      )}
    </Box>
  )
}
