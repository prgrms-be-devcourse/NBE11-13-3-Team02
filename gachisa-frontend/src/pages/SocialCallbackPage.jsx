import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import { useAuth } from '../context/AuthContext.jsx'
import { getErrorMessage } from '../api/errorMessage'
import { KAKAO_REDIRECT_URI, NAVER_REDIRECT_URI, consumeNaverState } from '../utils/socialAuth'
import LoadingScreen from '../components/LoadingScreen.jsx'

const PROVIDER_LABEL = { kakao: '카카오', naver: '네이버' }

export default function SocialCallbackPage() {
  const { provider } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { loginWithKakao, loginWithNaver } = useAuth()
  const [error, setError] = useState('')
  // OAuth code는 1회용이라, StrictMode의 effect 재실행 등으로 두 번 소비되면
  // 두 번째 요청은 반드시 실패한다. 세션당 한 번만 시도하도록 막는다.
  const ranRef = useRef(false)

  useEffect(() => {
    if (ranRef.current) return
    ranRef.current = true

    const code = searchParams.get('code')
    const state = searchParams.get('state')
    const providerError = searchParams.get('error')

    if (providerError) {
      setError('로그인이 취소되었습니다.')
      return
    }
    if (!code || (provider !== 'kakao' && provider !== 'naver')) {
      setError('잘못된 접근입니다.')
      return
    }

    const run = async () => {
      try {
        if (provider === 'kakao') {
          await loginWithKakao(code, KAKAO_REDIRECT_URI)
        } else {
          const savedState = consumeNaverState()
          if (!state || state !== savedState) {
            throw new Error('요청이 만료되었거나 위조되었습니다. 다시 시도해주세요.')
          }
          await loginWithNaver(code, NAVER_REDIRECT_URI, state)
        }
        navigate('/', { replace: true })
      } catch (err) {
        setError(getErrorMessage(err, err.message || '소셜 로그인에 실패했습니다.'))
      }
    }
    run()
  }, [provider, searchParams, loginWithKakao, loginWithNaver, navigate])

  if (error) {
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
          <Stack spacing={2.5}>
            <Typography variant="h6" fontWeight={800}>
              {PROVIDER_LABEL[provider] ?? '소셜'} 로그인 실패
            </Typography>
            <Alert severity="error">{error}</Alert>
            <Button variant="contained" onClick={() => navigate('/login')}>
              로그인으로 돌아가기
            </Button>
          </Stack>
        </Paper>
      </Box>
    )
  }

  return <LoadingScreen />
}
