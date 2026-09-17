import { useCallback, useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import Pagination from '@mui/material/Pagination'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import * as adminUserApi from '../api/adminUserApi'
import { getErrorMessage } from '../api/errorMessage'
import { formatDateTime } from '../utils/statusMeta'

const PAGE_SIZE = 20

const ROLE_LABEL = {
  ROLE_BUYER: '구매자',
  ROLE_SELLER: '판매자',
  ROLE_ADMIN: '관리자',
}

const STATUS_META = {
  ACTIVE: { label: '활성', color: 'success' },
  SUSPENDED: { label: '정지됨', color: 'warning' },
  WITHDRAWN: { label: '탈퇴함', color: 'default' },
}

// 정지/정지해제/강제탈퇴는 모두 같은 확인창을 재사용하고, 실행할 액션만 다르게 넘긴다.
const ACTION_META = {
  suspend: {
    title: '이 회원을 정지하시겠습니까?',
    body: (u) => `${u.name} (${u.email ?? '이메일 없음'}) 계정이 정지되어 로그인할 수 없게 됩니다.`,
    confirmLabel: '정지',
    confirmColor: 'warning',
    run: (userId) => adminUserApi.suspendUser(userId),
  },
  reinstate: {
    title: '정지를 해제하시겠습니까?',
    body: (u) => `${u.name} (${u.email ?? '이메일 없음'}) 계정을 다시 활성 상태로 되돌립니다.`,
    confirmLabel: '정지 해제',
    confirmColor: 'primary',
    run: (userId) => adminUserApi.reinstateUser(userId),
  },
  withdraw: {
    title: '이 회원을 강제 탈퇴시키겠습니까?',
    body: (u) => `${u.name} (${u.email ?? '이메일 없음'}) 계정을 강제 탈퇴시킵니다. 되돌릴 수 없습니다.`,
    confirmLabel: '강제 탈퇴',
    confirmColor: 'error',
    run: (userId) => adminUserApi.withdrawUser(userId),
  },
}

export default function AdminUserManagePage() {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState({ content: [], totalPages: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [pendingAction, setPendingAction] = useState(null) // { type, user }
  const [actionError, setActionError] = useState('')
  const [actionSubmitting, setActionSubmitting] = useState(false)

  const fetchUsers = useCallback(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    adminUserApi
      .getUsers({
        email: email || undefined,
        role: role || undefined,
        status: status || undefined,
        page,
        size: PAGE_SIZE,
      })
      .then(({ data }) => {
        if (!cancelled) setResult(data)
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err, '회원 목록을 불러오지 못했습니다.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [email, role, status, page])

  useEffect(() => fetchUsers(), [fetchUsers])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    setPage(0)
  }

  const openAction = (type, user) => {
    setActionError('')
    setPendingAction({ type, user })
  }

  const closeAction = () => {
    if (actionSubmitting) return
    setPendingAction(null)
  }

  const handleConfirmAction = async () => {
    if (!pendingAction) return
    const meta = ACTION_META[pendingAction.type]
    setActionSubmitting(true)
    setActionError('')
    try {
      await meta.run(pendingAction.user.id)
      setPendingAction(null)
      fetchUsers()
    } catch (err) {
      setActionError(getErrorMessage(err, '처리에 실패했습니다.'))
    } finally {
      setActionSubmitting(false)
    }
  }

  const content = result.content ?? []
  const actionMeta = pendingAction ? ACTION_META[pendingAction.type] : null

  return (
    <Box>
      <Typography variant="h5" fontWeight={800} gutterBottom>
        회원 관리
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        회원을 정지하거나 정지를 해제하고, 필요 시 강제 탈퇴시킬 수 있습니다. 관리자 계정은 정지/탈퇴 대상이 될 수
        없습니다.
      </Typography>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Box component="form" onSubmit={handleSearchSubmit}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
            <TextField
              label="이메일 검색"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              size="small"
              sx={{ minWidth: 220 }}
            />
            <TextField
              label="역할"
              select
              value={role}
              onChange={(e) => setRole(e.target.value)}
              size="small"
              sx={{ minWidth: 140 }}
            >
              <MenuItem value="">전체</MenuItem>
              <MenuItem value="ROLE_BUYER">구매자</MenuItem>
              <MenuItem value="ROLE_SELLER">판매자</MenuItem>
              <MenuItem value="ROLE_ADMIN">관리자</MenuItem>
            </TextField>
            <TextField
              label="상태"
              select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              size="small"
              sx={{ minWidth: 140 }}
            >
              <MenuItem value="">전체</MenuItem>
              <MenuItem value="ACTIVE">활성</MenuItem>
              <MenuItem value="SUSPENDED">정지됨</MenuItem>
              <MenuItem value="WITHDRAWN">탈퇴함</MenuItem>
            </TextField>
            <Button type="submit" variant="contained">
              검색
            </Button>
          </Stack>
        </Box>
      </Paper>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>이메일</TableCell>
              <TableCell>이름</TableCell>
              <TableCell>역할</TableCell>
              <TableCell>가입 경로</TableCell>
              <TableCell>상태</TableCell>
              <TableCell>가입일</TableCell>
              <TableCell align="right">작업</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading && content.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={24} />
                </TableCell>
              </TableRow>
            )}

            {!loading && content.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                  <Typography variant="body2" color="text.secondary">
                    조건에 맞는 회원이 없습니다.
                  </Typography>
                </TableCell>
              </TableRow>
            )}

            {content.map((u) => {
              const statusMeta = STATUS_META[u.status] ?? { label: u.status, color: 'default' }
              const manageable = u.role !== 'ROLE_ADMIN'
              return (
                <TableRow key={u.id} hover>
                  <TableCell>{u.id}</TableCell>
                  <TableCell>{u.email ?? '-'}</TableCell>
                  <TableCell>{u.name}</TableCell>
                  <TableCell>{ROLE_LABEL[u.role] ?? u.role}</TableCell>
                  <TableCell>{u.provider}</TableCell>
                  <TableCell>
                    <Chip size="small" label={statusMeta.label} color={statusMeta.color} />
                  </TableCell>
                  <TableCell>{formatDateTime(u.createdAt)}</TableCell>
                  <TableCell align="right">
                    {!manageable && (
                      <Typography variant="caption" color="text.secondary">
                        관리자 계정
                      </Typography>
                    )}
                    {manageable && u.status === 'ACTIVE' && (
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" color="warning" onClick={() => openAction('suspend', u)}>
                          정지
                        </Button>
                        <Button size="small" color="error" onClick={() => openAction('withdraw', u)}>
                          강제 탈퇴
                        </Button>
                      </Stack>
                    )}
                    {manageable && u.status === 'SUSPENDED' && (
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" onClick={() => openAction('reinstate', u)}>
                          정지 해제
                        </Button>
                        <Button size="small" color="error" onClick={() => openAction('withdraw', u)}>
                          강제 탈퇴
                        </Button>
                      </Stack>
                    )}
                    {manageable && u.status === 'WITHDRAWN' && (
                      <Typography variant="caption" color="text.secondary">
                        탈퇴한 회원
                      </Typography>
                    )}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>

      {result.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination
            page={page + 1}
            count={result.totalPages}
            onChange={(_, value) => setPage(value - 1)}
            color="primary"
          />
        </Box>
      )}

      <Dialog open={!!pendingAction} onClose={closeAction} fullWidth maxWidth="xs">
        {actionMeta && (
          <>
            <DialogTitle>{actionMeta.title}</DialogTitle>
            <DialogContent>
              <DialogContentText>{actionMeta.body(pendingAction.user)}</DialogContentText>
              {actionError && (
                <Alert severity="error" sx={{ mt: 2 }}>
                  {actionError}
                </Alert>
              )}
            </DialogContent>
            <DialogActions>
              <Button onClick={closeAction} disabled={actionSubmitting}>
                취소
              </Button>
              <Button
                color={actionMeta.confirmColor}
                variant="contained"
                onClick={handleConfirmAction}
                disabled={actionSubmitting}
              >
                {actionSubmitting ? '처리 중...' : actionMeta.confirmLabel}
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </Box>
  )
}
