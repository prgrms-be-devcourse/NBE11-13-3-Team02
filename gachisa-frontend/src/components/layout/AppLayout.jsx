import { useEffect, useState } from 'react'
import { Outlet, useNavigate, useSearchParams } from 'react-router-dom'
import AppBar from '@mui/material/AppBar'
import Toolbar from '@mui/material/Toolbar'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Avatar from '@mui/material/Avatar'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Divider from '@mui/material/Divider'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Alert from '@mui/material/Alert'
import SearchIcon from '@mui/icons-material/Search'
import CloseIcon from '@mui/icons-material/Close'
import Logo from '../Logo.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { getCategories } from '../../api/categoryApi'
import CategoryTreeSelect from '../CategoryTreeSelect.jsx'

const emptyForm = { keyword: '', categoryId: '', minPrice: '', maxPrice: '' }

export default function AppLayout() {
  const { user, isAuthenticated, isBuyer, isSeller, isAdmin, logout } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [anchorEl, setAnchorEl] = useState(null)
  const [searchOpen, setSearchOpen] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [filterError, setFilterError] = useState('')
  const [categories, setCategories] = useState([])

  useEffect(() => {
    getCategories()
      .then(({ data }) => setCategories(data ?? []))
      .catch(() => setCategories([]))
  }, [])

  const goTo = (path) => {
    setAnchorEl(null)
    navigate(path)
  }

  const openSearch = () => {
    setForm({
      keyword: searchParams.get('keyword') ?? '',
      categoryId: searchParams.get('categoryId') ?? '',
      minPrice: searchParams.get('minPrice') ?? '',
      maxPrice: searchParams.get('maxPrice') ?? '',
    })
    setFilterError('')
    setSearchOpen(true)
  }

  const handleFormChange = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }))

  // 가격 필드는 음수 입력 자체를 막는다 (타이핑/붙여넣기 모두 무시)
  const handlePriceChange = (field) => (e) => {
    const value = e.target.value
    if (value !== '' && Number(value) < 0) return
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    setFilterError('')

    const min = form.minPrice === '' ? null : Number(form.minPrice)
    const max = form.maxPrice === '' ? null : Number(form.maxPrice)

    if ((min !== null && min < 0) || (max !== null && max < 0)) {
      setFilterError('가격은 0원 이상으로 입력해주세요.')
      return
    }
    if (min !== null && max !== null && min > max) {
      setFilterError('최소 가격이 최대 가격보다 클 수 없습니다.')
      return
    }

    const params = new URLSearchParams()
    if (form.keyword) params.set('keyword', form.keyword)
    if (form.categoryId) params.set('categoryId', form.categoryId)
    if (form.minPrice !== '') params.set('minPrice', form.minPrice)
    if (form.maxPrice !== '') params.set('maxPrice', form.maxPrice)

    setSearchOpen(false)
    navigate(`/${params.toString() ? `?${params.toString()}` : ''}`)
  }

  const handleLogout = async () => {
    setAnchorEl(null)
    await logout()
    navigate('/login')
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="static" color="inherit" sx={{ bgcolor: 'background.paper' }}>
        <Toolbar sx={{ gap: 2, py: 1 }}>
          <Box sx={{ cursor: 'pointer' }} onClick={() => navigate('/')}>
            <Logo size={32} />
          </Box>

          <Box sx={{ flexGrow: 1 }} />

          <IconButton onClick={openSearch} aria-label="검색">
            <SearchIcon />
          </IconButton>

          {isAuthenticated ? (
            <>
              <IconButton onClick={(e) => setAnchorEl(e.currentTarget)}>
                <Avatar sx={{ width: 34, height: 34, bgcolor: 'primary.light', color: 'primary.main' }}>
                  {user?.name?.[0] ?? '?'}
                </Avatar>
              </IconButton>
              <Menu anchorEl={anchorEl} open={!!anchorEl} onClose={() => setAnchorEl(null)}>
                <MenuItem disabled>
                  <Typography variant="body2" fontWeight={700}>
                    {user?.name} 님
                  </Typography>
                </MenuItem>
                {isBuyer && (
                  <>
                    <Divider />
                    <MenuItem onClick={() => goTo('/my/participations')}>내 참여내역</MenuItem>
                    <MenuItem onClick={() => goTo('/my/page')}>마이페이지</MenuItem>
                  </>
                )}
                {isSeller && (
                  <>
                    <Divider />
                    <MenuItem onClick={() => goTo('/')}>공동구매 목록</MenuItem>
                    <Divider />
                    <MenuItem onClick={() => goTo('/my/products')}>내 상품 관리</MenuItem>
                    <MenuItem onClick={() => goTo('/products/new')}>상품 등록</MenuItem>
                    <MenuItem onClick={() => goTo('/group-buys/new')}>공동구매 등록</MenuItem>
                    <MenuItem onClick={() => goTo('/dev/concurrency')}>동시성 검증</MenuItem>
                  </>
                )}
                {isAdmin && (
                  <>
                    <Divider />
                    <MenuItem onClick={() => goTo('/admin/categories')}>카테고리 관리</MenuItem>
                    <MenuItem onClick={() => goTo('/admin/deliveries')}>배송 관리</MenuItem>
                    <MenuItem onClick={() => goTo('/dev/concurrency')}>동시성 검증</MenuItem>
                  </>
                )}
                <Divider />
                <MenuItem onClick={handleLogout}>로그아웃</MenuItem>
              </Menu>
            </>
          ) : (
            <Button variant="contained" onClick={() => navigate('/login')}>
              로그인
            </Button>
          )}
        </Toolbar>
      </AppBar>

      <Dialog
        open={searchOpen}
        onClose={() => setSearchOpen(false)}
        fullWidth
        maxWidth="sm"
        sx={{ '& .MuiDialog-container': { alignItems: 'flex-start' } }}
        PaperProps={{ sx: { mt: 10, borderRadius: 3 } }}
      >
        <Box component="form" onSubmit={handleSearchSubmit} sx={{ p: 3 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Typography variant="h6" fontWeight={800}>
              상세검색
            </Typography>
            <IconButton onClick={() => setSearchOpen(false)} aria-label="닫기" size="small">
              <CloseIcon fontSize="small" />
            </IconButton>
          </Stack>

          <Stack spacing={2}>
            <TextField
              label="키워드"
              placeholder="어떤 공동구매를 찾으세요?"
              value={form.keyword}
              onChange={handleFormChange('keyword')}
              autoFocus
              fullWidth
              size="small"
            />
            <CategoryTreeSelect
              categories={categories}
              value={form.categoryId}
              onChange={(id) => setForm((prev) => ({ ...prev, categoryId: id }))}
              fullWidth
            />
            <Stack direction="row" spacing={2}>
              <TextField
                label="최소 가격"
                type="number"
                value={form.minPrice}
                onChange={handlePriceChange('minPrice')}
                inputProps={{ min: 0 }}
                fullWidth
                size="small"
              />
              <TextField
                label="최대 가격"
                type="number"
                value={form.maxPrice}
                onChange={handlePriceChange('maxPrice')}
                inputProps={{ min: 0 }}
                fullWidth
                size="small"
              />
            </Stack>
            {filterError && <Alert severity="warning">{filterError}</Alert>}
            <Button type="submit" variant="contained" size="large" fullWidth>
              검색
            </Button>
          </Stack>
        </Box>
      </Dialog>

      <Box sx={{ maxWidth: 1200, mx: 'auto', px: { xs: 2, sm: 3 }, py: 4 }}>
        <Outlet />
      </Box>
    </Box>
  )
}
