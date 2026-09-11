import { useCallback, useEffect, useState } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Stack from '@mui/material/Stack'
import Button from '@mui/material/Button'
import TextField from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'
import Alert from '@mui/material/Alert'
import Divider from '@mui/material/Divider'
import LinearProgress from '@mui/material/LinearProgress'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import IconButton from '@mui/material/IconButton'
import RefreshIcon from '@mui/icons-material/Refresh'
import { runConcurrencyStress, getDevGroupBuyList } from '../api/concurrencyApi'
import { getParticipationCount } from '../api/participationApi'
import { getErrorMessage } from '../api/errorMessage'

const MODES = [
  {
    value: 'UNSAFE',
    label: 'UNSAFE (락 없음)',
    hint: 'read-check-write. 초과 모집(oversell)이 날 수 있음',
  },
  {
    value: 'DB_LOCK',
    label: 'DB_LOCK (비관적 락)',
    hint: 'SELECT FOR UPDATE만 사용',
  },
  {
    value: 'REDIS_AND_DB',
    label: '분산락 (Redis + DB)',
    hint: '분산락(Redis 원자 예약)으로 먼저 걸러내고, DB 비관적 락으로 최종 확정',
  },
]

export default function ConcurrencyDemoPage() {
  const [groupBuyList, setGroupBuyList] = useState([])
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState('')
  const [groupBuyId, setGroupBuyId] = useState('')
  const [mode, setMode] = useState('UNSAFE')
  const [threadCount, setThreadCount] = useState(30)
  const [quantityPerRequest, setQuantityPerRequest] = useState(1)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)
  const [liveCount, setLiveCount] = useState(null)

  // DB에 있는 공동구매 목록을 내려받아 드롭다운으로 고를 수 있게 함
  const loadGroupBuyList = useCallback(async () => {
    setListLoading(true)
    setListError('')
    try {
      const { data } = await getDevGroupBuyList()
      setGroupBuyList(data ?? [])
      // 이전에 고른 groupBuyId가 목록에 더 이상 없으면 선택 해제
      if (groupBuyId && !(data ?? []).some((item) => String(item.id) === String(groupBuyId))) {
        setGroupBuyId('')
      }
    } catch (err) {
      setListError(getErrorMessage(err, '공동구매 목록을 불러오지 못했습니다.'))
    } finally {
      setListLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    loadGroupBuyList()
  }, [loadGroupBuyList])

  const refreshCount = useCallback(() => {
    if (!groupBuyId) return
    getParticipationCount(groupBuyId)
        .then(({ data }) => setLiveCount(data))
        .catch(() => setLiveCount(null))
  }, [groupBuyId])

  useEffect(() => {
    if (!groupBuyId) {
      setLiveCount(null)
      return undefined
    }
    refreshCount()
    const timer = setInterval(refreshCount, 1000)
    return () => clearInterval(timer)
  }, [groupBuyId, refreshCount])

  const handleRun = async () => {
    setError('')
    setResult(null)
    if (!groupBuyId) {
      setError('공동구매를 선택하세요.')
      return
    }
    setRunning(true)
    try {
      const { data } = await runConcurrencyStress(groupBuyId, {
        mode,
        threadCount: Number(threadCount),
        quantityPerRequest: Number(quantityPerRequest),
      })
      setResult(data)
      refreshCount()
    } catch (err) {
      setError(
          getErrorMessage(
              err,
              '동시성 테스트에 실패했습니다. local 프로필·판매자/관리자 로그인·Redis를 확인하세요.',
          ),
      )
    } finally {
      setRunning(false)
    }
  }

  const progress =
      liveCount && liveCount.targetCount > 0
          ? Math.min(100, (liveCount.currentCount / liveCount.targetCount) * 100)
          : 0

  return (
      <Box sx={{ maxWidth: 720, mx: 'auto' }}>
        <Typography variant="h5" fontWeight={800} gutterBottom>
          공동구매 동시성 검증
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          같은 공동구매에 동시에 예약을 걸어 currentCount가 target을 넘는지 확인합니다. 실행 시 DB/Redis
          currentCount를 0으로 되돌린 뒤 경쟁하므로, 이미 마감된 공동구매여도 테스트할 수 있습니다.
          Participation 레코드는 만들지 않습니다. (local 프로필 전용 API)
        </Typography>

        <Paper sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="flex-start">
              <TextField
                  select
                  label="공동구매 선택"
                  value={groupBuyId}
                  onChange={(e) => setGroupBuyId(e.target.value)}
                  size="small"
                  fullWidth
                  disabled={listLoading}
                  helperText={
                    listLoading
                        ? '목록 불러오는 중...'
                        : groupBuyList.length === 0
                            ? 'DB에 등록된 공동구매가 없습니다.'
                            : undefined
                  }
              >
                {groupBuyList.map((item) => (
                    <MenuItem key={item.id} value={item.id}>
                      #{item.id} · {item.productName} · {item.status} ({item.currentCount}/{item.targetCount})
                    </MenuItem>
                ))}
              </TextField>
              <IconButton
                  onClick={loadGroupBuyList}
                  disabled={listLoading}
                  size="small"
                  sx={{ mt: 0.5 }}
                  title="목록 새로고침"
              >
                {listLoading ? <CircularProgress size={18} /> : <RefreshIcon fontSize="small" />}
              </IconButton>
            </Stack>
            {listError && <Alert severity="warning">{listError}</Alert>}

            <TextField
                select
                label="모드"
                value={mode}
                onChange={(e) => setMode(e.target.value)}
                size="small"
                fullWidth
                helperText={MODES.find((m) => m.value === mode)?.hint}
            >
              {MODES.map((m) => (
                  <MenuItem key={m.value} value={m.value}>
                    {m.label}
                  </MenuItem>
              ))}
            </TextField>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                  label="동시 요청 수"
                  type="number"
                  value={threadCount}
                  onChange={(e) => setThreadCount(e.target.value)}
                  inputProps={{ min: 2, max: 80 }}
                  size="small"
                  fullWidth
              />
              <TextField
                  label="요청당 수량"
                  type="number"
                  value={quantityPerRequest}
                  onChange={(e) => setQuantityPerRequest(e.target.value)}
                  inputProps={{ min: 1, max: 10 }}
                  size="small"
                  fullWidth
              />
            </Stack>

            {liveCount && (
                <Box>
                  <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                    <Typography variant="body2" fontWeight={700}>
                      실시간 인원 (Redis)
                    </Typography>
                    <Typography variant="body2">
                      {liveCount.currentCount}/{liveCount.targetCount}
                    </Typography>
                  </Stack>
                  <LinearProgress variant="determinate" value={progress} sx={{ height: 8, borderRadius: 4 }} />
                </Box>
            )}

            <Button variant="contained" size="large" disabled={running || !groupBuyId} onClick={handleRun}>
              {running ? '실행 중…' : '동시성 테스트 실행'}
            </Button>

            {error && <Alert severity="error">{error}</Alert>}
          </Stack>
        </Paper>

        {result && (() => {
          const hasRejectedAsFull = (result.rejectedAsFull ?? result.failureCount ?? 0) > 0
          const hasOtherFailures = (result.otherFailures ?? 0) > 0
          const isTestSuccess = !result.oversold && !hasOtherFailures

          return (
              <Paper sx={{ p: 3, mt: 3 }}>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                  <Typography variant="h6" fontWeight={800}>
                    결과
                  </Typography>
                  <Chip
                      size="small"
                      label={
                        result.oversold
                            ? '초과 모집 발생'
                            : hasOtherFailures
                                ? '테스트 실패'
                                : '정원 준수'
                      }
                      color={
                        result.oversold
                            ? 'error'
                            : hasOtherFailures
                                ? 'warning'
                                : 'success'
                      }
                  />
                </Stack>
                <Alert
                    severity={
                      result.oversold
                          ? 'error'
                          : hasOtherFailures
                              ? 'warning'
                              : 'success'
                    }
                    sx={{ mb: 2 }}
                >
                  {result.summary}
                </Alert>
                <Divider sx={{ mb: 2 }} />
                <Stack spacing={0.8}>
                  <Typography variant="body2">
                    모드: {MODES.find((m) => m.value === result.mode)?.label ?? result.mode}
                  </Typography>
                  <Typography variant="body2">
                    성공 {result.successCount} / 정원초과거절 {result.rejectedAsFull ?? result.failureCount} / 기타실패{' '}
                    {result.otherFailures ?? 0} (스레드 {result.threadCount})
                  </Typography>
                  <Typography variant="body2">
                    시작 전 DB currentCount: {result.currentCountBefore} → 테스트는 0부터 다시 채움 (target{' '}
                    {result.targetCount})
                  </Typography>
                  <Typography variant="body2">
                    테스트 후 DB currentCount: {result.currentCountAfterDb}
                    {result.mode === 'REDIS_AND_DB' && (
                        <> / Redis: {result.currentCountAfterRedis ?? '-'}</>
                    )}
                  </Typography>
                  {result.successCount > result.targetCount && result.currentCountAfterDb <= result.targetCount && (
                      <Typography variant="caption" color="error.main">
                        ⚠ 성공 응답 수({result.successCount})가 정원({result.targetCount})을 넘었는데 DB값은 낮습니다.
                        이건 Lost Update로 일부 반영이 유실됐다는 뜻입니다.
                      </Typography>
                  )}
                </Stack>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
                  권장 확인 순서: ① UNSAFE로 oversold 확인 → ② 분산락(REDIS_AND_DB)로 정원 준수 확인
                </Typography>
              </Paper>
          )
        })()}
      </Box>
  )
}