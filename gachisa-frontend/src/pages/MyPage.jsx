import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Divider from '@mui/material/Divider'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogActions from '@mui/material/DialogActions'
import { useAuth } from '../context/AuthContext.jsx'
import * as userApi from '../api/userApi'
import { getErrorMessage } from '../api/errorMessage'
import { formatDateTime } from '../utils/statusMeta'
import SavedDeliveryAddressManager from '../components/SavedDeliveryAddressManager.jsx'

const ROLE_LABEL = {
  ROLE_BUYER: '구매자',
  ROLE_SELLER: '판매자',
  ROLE_ADMIN: '관리자',
}

export default function MyPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const returnTo = searchParams.get('returnTo')
  const { user, refreshUser, withdraw } = useAuth()
  const [name, setName] = useState(user?.name ?? '')
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const [withdrawOpen, setWithdrawOpen] = useState(false)
  const [withdrawPassword, setWithdrawPassword] = useState('')
  const [withdrawError, setWithdrawError] = useState('')
  const [withdrawing, setWithdrawing] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    setSubmitting(true)
    try {
      const payload = { name }
      if (currentPassword && newPassword) {
        payload.currentPassword = currentPassword
        payload.newPassword = newPassword
      }
      await userApi.updateMe(payload)
      await refreshUser()
      setCurrentPassword('')
      setNewPassword('')
      setSuccess('수정되었습니다.')
    } catch (err) {
      setError(getErrorMessage(err, '정보 수정에 실패했습니다.'))
    } finally {
      setSubmitting(false)
    }
  }

  const openWithdraw = () => {
    setWithdrawPassword('')
    setWithdrawError('')
    setWithdrawOpen(true)
  }

  const handleWithdraw = async () => {
    setWithdrawError('')
    setWithdrawing(true)
    try {
      await withdraw(withdrawPassword || undefined)
      navigate('/login', { replace: true })
    } catch (err) {
      setWithdrawError(getErrorMessage(err, '회원 탈퇴에 실패했습니다.'))
    } finally {
      setWithdrawing(false)
    }
  }

  if (!user) return null

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>
        마이페이지
      </Typography>

      <Paper sx={{ p: 3, mb: 3, maxWidth: 480 }} elevation={1}>
        <Stack spacing={1.5}>
          <Stack direction="row" spacing={2}>
            <Typography variant="body2" color="text.secondary" sx={{ width: 100 }}>
              회원 ID
            </Typography>
            <Typography variant="body2">{user.id}</Typography>
          </Stack>
          <Stack direction="row" spacing={2}>
            <Typography variant="body2" color="text.secondary" sx={{ width: 100 }}>
              이메일
            </Typography>
            <Typography variant="body2">{user.email}</Typography>
          </Stack>
          <Stack direction="row" spacing={2}>
            <Typography variant="body2" color="text.secondary" sx={{ width: 100 }}>
              이름
            </Typography>
            <Typography variant="body2">{user.name}</Typography>
          </Stack>
          <Stack direction="row" spacing={2}>
            <Typography variant="body2" color="text.secondary" sx={{ width: 100 }}>
              역할
            </Typography>
            <Typography variant="body2">{ROLE_LABEL[user.role] ?? user.role}</Typography>
          </Stack>
          <Stack direction="row" spacing={2}>
            <Typography variant="body2" color="text.secondary" sx={{ width: 100 }}>
              가입일
            </Typography>
            <Typography variant="body2">{formatDateTime(user.createdAt)}</Typography>
          </Stack>
        </Stack>
      </Paper>

      <Paper sx={{ p: 3, maxWidth: 480 }} elevation={1}>
        <Typography variant="subtitle1" fontWeight={700} gutterBottom>
          정보 수정
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            {success && <Alert severity="success">{success}</Alert>}
            <TextField label="이름" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />

            <Button
              size="small"
              onClick={() => setShowPasswordForm((prev) => !prev)}
              sx={{ alignSelf: 'flex-start' }}
            >
              {showPasswordForm ? '비밀번호 변경 취소' : '비밀번호 변경'}
            </Button>

            {showPasswordForm && (
              <Stack spacing={2} sx={{ pl: 1, borderLeft: '2px solid', borderColor: 'divider' }}>
                <TextField
                  label="현재 비밀번호"
                  type="password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  fullWidth
                />
                <TextField
                  label="새 비밀번호"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  fullWidth
                />
              </Stack>
            )}

            <Button type="submit" variant="contained" size="large" disabled={submitting}>
              {submitting ? '저장 중...' : '저장'}
            </Button>
          </Stack>
        </Box>
      </Paper>
      <SavedDeliveryAddressManager onSaved={returnTo ? () => navigate(returnTo) : undefined} />

      <Paper sx={{ p: 3, mt: 3, maxWidth: 480, borderColor: 'error.main', borderWidth: 1, borderStyle: 'solid' }}>
        <Typography variant="subtitle1" fontWeight={700} color="error.main" gutterBottom>
          회원 탈퇴
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          탈퇴하면 계정이 즉시 비활성화되고 되돌릴 수 없습니다. 같은 이메일로는 24시간 이후에 다시 가입할 수
          있습니다.
        </Typography>
        <Button variant="outlined" color="error" onClick={openWithdraw}>
          회원 탈퇴
        </Button>
      </Paper>

      <Dialog open={withdrawOpen} onClose={() => setWithdrawOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>정말 탈퇴하시겠습니까?</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            탈퇴 후에는 되돌릴 수 없습니다. 비밀번호로 로그인하는 계정이라면 확인을 위해 비밀번호를 입력해주세요.
            소셜 로그인 전용 계정은 비워두셔도 됩니다.
          </DialogContentText>
          <TextField
            label="비밀번호"
            type="password"
            value={withdrawPassword}
            onChange={(e) => setWithdrawPassword(e.target.value)}
            fullWidth
            autoFocus
          />
          {withdrawError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {withdrawError}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setWithdrawOpen(false)} disabled={withdrawing}>
            취소
          </Button>
          <Button color="error" variant="contained" onClick={handleWithdraw} disabled={withdrawing}>
            {withdrawing ? '탈퇴 처리 중...' : '탈퇴하기'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
