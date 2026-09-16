import { useCallback, useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import RefreshIcon from '@mui/icons-material/Refresh'
import { getAdminChatUsage } from '../api/chatApi.js'

const format = (value) => value.toLocaleString()

export default function AdminChatUsagePage() {
  const [usage, setUsage] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setUsage(await getAdminChatUsage())
      setError('')
    } catch (requestError) {
      setError(
        requestError.status === 403
          ? '관리자만 조회할 수 있습니다.'
          : '사용량을 불러오지 못했습니다. 챗봇 서버가 떠 있는지 확인해주세요.',
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const users = usage?.users ?? []
  const grandTotal = (usage?.totalInputTokens ?? 0) + (usage?.totalOutputTokens ?? 0)

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Box>
          <Typography variant="h5" fontWeight={800}>
            챗봇 토큰 사용량
          </Typography>
          <Typography variant="body2" color="text.secondary">
            사용자별 AI 토큰 사용량입니다. 집계는 챗봇 서버 메모리에 있어 서버를 재시작하면
            누적치가 0부터 다시 쌓입니다.
          </Typography>
        </Box>
        <Button startIcon={<RefreshIcon />} onClick={load} disabled={loading}>
          새로고침
        </Button>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}

      {!error && (
        <>
          <Stack direction="row" spacing={1} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
            <Chip label={`전체 누적 ${format(grandTotal)} 토큰`} color="primary" />
            <Chip label={`입력 ${format(usage?.totalInputTokens ?? 0)}`} variant="outlined" />
            <Chip label={`출력 ${format(usage?.totalOutputTokens ?? 0)}`} variant="outlined" />
            <Chip label={`1인당 하루 ${usage?.dailyMessageLimit ?? 0}회 제한`} variant="outlined" />
          </Stack>

          <TableContainer component={Paper}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>사용자</TableCell>
                  <TableCell align="right">오늘 대화</TableCell>
                  <TableCell align="right">오늘 토큰</TableCell>
                  <TableCell align="right">누적 대화</TableCell>
                  <TableCell align="right">누적 입력</TableCell>
                  <TableCell align="right">누적 출력</TableCell>
                  <TableCell align="right">누적 합계</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading && users.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                      <CircularProgress size={24} />
                    </TableCell>
                  </TableRow>
                )}

                {!loading && users.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                      <Typography variant="body2" color="text.secondary">
                        아직 챗봇을 사용한 기록이 없습니다.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}

                {users.map((row) => (
                  <TableRow key={row.userId} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={700}>
                        {row.name || '(이름 없음)'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        #{row.userId}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      {row.dailyMessagesUsed} / {usage?.dailyMessageLimit ?? 0}
                    </TableCell>
                    <TableCell align="right">
                      {format(row.dailyInputTokens + row.dailyOutputTokens)}
                    </TableCell>
                    <TableCell align="right">{format(row.totalMessagesUsed)}</TableCell>
                    <TableCell align="right">{format(row.totalInputTokens)}</TableCell>
                    <TableCell align="right">{format(row.totalOutputTokens)}</TableCell>
                    <TableCell align="right">
                      <Typography variant="body2" fontWeight={700}>
                        {format(row.totalInputTokens + row.totalOutputTokens)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}
    </Box>
  )
}
