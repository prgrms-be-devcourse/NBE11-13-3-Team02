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
  const { user, refreshUser } = useAuth()
  const [name, setName] = useState(user?.name ?? '')
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)

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
    </Box>
  )
}
