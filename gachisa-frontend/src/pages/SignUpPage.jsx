import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import { useAuth } from '../context/AuthContext.jsx'
import { getErrorMessage } from '../api/errorMessage'
import Logo from '../components/Logo.jsx'

export default function SignUpPage() {
  const { signUp } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '', name: '', role: 'ROLE_BUYER' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleChange = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await signUp(form)
      navigate('/login', { replace: true, state: { signedUp: true } })
    } catch (err) {
      setError(getErrorMessage(err, '회원가입에 실패했습니다.'))
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
          같이사에서 함께 구매하고 더 저렴하게 만나보세요
        </Typography>

        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2.5}>
            {error && <Alert severity="error">{error}</Alert>}

            <Box>
              <Typography variant="body2" fontWeight={700} sx={{ mb: 0.8 }}>
                이름
              </Typography>
              <TextField value={form.name} onChange={handleChange('name')} required fullWidth size="small" />
            </Box>

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

            <Box>
              <Typography variant="body2" fontWeight={700} sx={{ mb: 0.8 }}>
                가입 유형
              </Typography>
              <ToggleButtonGroup
                exclusive
                fullWidth
                value={form.role}
                onChange={(_, value) => value && setForm((prev) => ({ ...prev, role: value }))}
              >
                <ToggleButton value="ROLE_BUYER">구매자</ToggleButton>
                <ToggleButton value="ROLE_SELLER">판매자</ToggleButton>
              </ToggleButtonGroup>
            </Box>

            <Button type="submit" variant="contained" size="large" disabled={submitting} sx={{ py: 1.4 }}>
              {submitting ? '가입 중...' : '회원가입'}
            </Button>
          </Stack>
        </Box>

        <Typography variant="body2" textAlign="center" sx={{ mt: 3.5 }}>
          이미 계정이 있으신가요?{' '}
          <Typography component={Link} to="/login" variant="body2" fontWeight={700} color="primary.main" sx={{ textDecoration: 'none' }}>
            로그인
          </Typography>
        </Typography>
      </Paper>
    </Box>
  )
}
