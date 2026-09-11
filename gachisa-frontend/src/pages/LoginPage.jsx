import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import Divider from '@mui/material/Divider'
import Snackbar from '@mui/material/Snackbar'
import { useAuth } from '../context/AuthContext.jsx'
import { getErrorMessage } from '../api/errorMessage'
import Logo from '../components/Logo.jsx'
import { buildKakaoAuthUrl, buildNaverAuthUrl, isKakaoLoginEnabled, isNaverLoginEnabled } from '../utils/socialAuth'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [soonOpen, setSoonOpen] = useState(false)

  const handleChange = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }))

  const handleKakaoLogin = () => {
    if (!isKakaoLoginEnabled()) {
      setSoonOpen(true)
      return
    }
    window.location.href = buildKakaoAuthUrl()
  }

  const handleNaverLogin = () => {
    if (!isNaverLoginEnabled()) {
      setSoonOpen(true)
      return
    }
    window.location.href = buildNaverAuthUrl()
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await login(form.email, form.password)
      navigate(location.state?.from?.pathname ?? '/', { replace: true })
    } catch (err) {
      setError(getErrorMessage(err, '로그인에 실패했습니다. 이메일/비밀번호를 확인해주세요.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        bgcolor: 'background.default',
        px: 2,
      }}
    >
      <Paper sx={{ p: 5, width: 440, borderRadius: 4 }} elevation={0} variant="outlined">
        <Logo size={36} textVariant="h5" />
        <Typography color="text.secondary" sx={{ mt: 1.5, mb: 3 }}>
          로그인하고 공동구매에 참여해보세요
        </Typography>

        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2.5}>
            {error && <Alert severity="error">{error}</Alert>}

            <Box>
              <Typography variant="body2" fontWeight={700} sx={{ mb: 0.8 }}>
                이메일
              </Typography>
              <TextField
                placeholder="name@example.com"
                type="email"
                value={form.email}
                onChange={handleChange('email')}
                required
                fullWidth
                size="small"
              />
            </Box>

            <Box>
              <Typography variant="body2" fontWeight={700} sx={{ mb: 0.8 }}>
                비밀번호
              </Typography>
              <TextField
                placeholder="8자 이상 입력"
                type="password"
                value={form.password}
                onChange={handleChange('password')}
                required
                fullWidth
                size="small"
              />
            </Box>

            <Button type="submit" variant="contained" size="large" disabled={submitting} sx={{ py: 1.4 }}>
              {submitting ? '로그인 중...' : '로그인'}
            </Button>

            <Divider sx={{ color: 'text.secondary', fontSize: 13 }}>또는</Divider>

            <Button
              size="large"
              onClick={handleKakaoLogin}
              sx={{ bgcolor: '#FEE500', color: '#3C1E1E', py: 1.2, '&:hover': { bgcolor: '#FADA00' } }}
            >
              카카오로 시작하기
            </Button>
            <Button
              size="large"
              onClick={handleNaverLogin}
              sx={{ bgcolor: '#03C75A', color: '#fff', py: 1.2, '&:hover': { bgcolor: '#02B350' } }}
            >
              네이버로 시작하기
            </Button>
          </Stack>
        </Box>

        <Typography variant="body2" textAlign="center" sx={{ mt: 3.5 }}>
          계정이 없으신가요?{' '}
          <Typography component={Link} to="/signup" variant="body2" fontWeight={700} color="primary.main" sx={{ textDecoration: 'none' }}>
            회원가입
          </Typography>
        </Typography>
      </Paper>

      <Snackbar
        open={soonOpen}
        autoHideDuration={2500}
        onClose={() => setSoonOpen(false)}
        message="소셜 로그인은 준비 중이에요."
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Box>
  )
}
