import { useCallback, useEffect, useRef, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Fab from '@mui/material/Fab'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline'
import CloseIcon from '@mui/icons-material/Close'
import SendIcon from '@mui/icons-material/Send'
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined'
import { streamChat } from '../api/chatApi'
import { resizeImage } from '../utils/resizeImage'
import { useAuth } from '../context/AuthContext.jsx'

const TOOL_LABELS = {
  search_group_buys: '공동구매 검색 중',
  get_my_orders: '주문 내역 확인 중',
  get_order_delivery: '배송 정보 확인 중',
}

const GREETING =
  '무엇을 도와드릴까요? 공동구매 검색, 주문 확인, 배송 조회를 할 수 있어요.\n상품 사진을 올리면 비슷한 공동구매를 찾아드려요.'

export default function ChatWidget() {
  const { isAuthenticated } = useAuth()
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [activeTool, setActiveTool] = useState(null)
  const [error, setError] = useState('')
  const [image, setImage] = useState(null)
  const conversationIdRef = useRef(null)
  const abortRef = useRef(null)
  const bottomRef = useRef(null)
  const fileRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, activeTool])

  // 패널을 닫거나 화면을 떠나면 진행 중인 스트림을 끊는다.
  useEffect(() => () => abortRef.current?.abort(), [])

  const closePanel = useCallback(() => {
    abortRef.current?.abort()
    setOpen(false)
  }, [])

  const send = useCallback(async () => {
    const text = input.trim() || (image ? '이 사진과 비슷한 공동구매 찾아줘' : '')
    if (!text || busy) return

    // 빈 어시스턴트 말풍선(직전 요청이 실패했을 때 남는다)은 기록에서 뺀다.
    const history = messages.filter((m) => m.content).map(({ role, content }) => ({ role, content }))

    const attached = image
    setInput('')
    setImage(null)
    setError('')
    setMessages((prev) => [
      ...prev,
      { role: 'user', content: text, imageUrl: attached?.previewUrl },
      { role: 'assistant', content: '' },
    ])
    setBusy(true)

    const controller = new AbortController()
    abortRef.current = controller

    try {
      await streamChat({
        message: text,
        history,
        conversationId: conversationIdRef.current,
        image: attached ? { mimeType: attached.mimeType, data: attached.data } : null,
        signal: controller.signal,
        onEvent: (event, data) => {
          if (event === 'start') {
            conversationIdRef.current = data.conversationId
          } else if (event === 'tool') {
            setActiveTool(data.name)
          } else if (event === 'token') {
            setActiveTool(null)
            setMessages((prev) => {
              const next = [...prev]
              const last = next[next.length - 1]
              next[next.length - 1] = { ...last, content: last.content + data.text }
              return next
            })
          } else if (event === 'error') {
            setError(data.message)
          }
        },
      })
    } catch (e) {
      if (e.name !== 'AbortError') {
        setError(e.status === 401 ? '로그인이 필요합니다.' : '챗봇에 연결하지 못했습니다.')
      }
    } finally {
      setBusy(false)
      setActiveTool(null)
      abortRef.current = null
    }
  }, [input, busy, messages, image])

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      send()
    }
  }

  const pickImage = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 파일을 다시 골라도 change가 발생하도록 비운다
    if (!file) return
    try {
      setImage(await resizeImage(file))
      setError('')
    } catch {
      setError('이미지를 읽지 못했습니다.')
    }
  }

  if (!isAuthenticated) return null

  if (!open) {
    return (
      <Fab
        color="primary"
        aria-label="챗봇 열기"
        onClick={() => setOpen(true)}
        sx={{ position: 'fixed', right: 24, bottom: 24 }}
      >
        <ChatBubbleOutlineIcon />
      </Fab>
    )
  }

  const visible = messages.filter((m, i) => m.content || i === messages.length - 1)

  return (
    <Paper
      elevation={8}
      sx={{
        position: 'fixed',
        right: 24,
        bottom: 24,
        width: { xs: 'calc(100vw - 32px)', sm: 380 },
        height: 540,
        maxHeight: 'calc(100vh - 48px)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        zIndex: (theme) => theme.zIndex.modal,
      }}
    >
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ px: 2, py: 1.5, bgcolor: 'primary.main', color: 'primary.contrastText' }}
      >
        <Typography variant="subtitle1" fontWeight={700}>
          가치사 도우미
        </Typography>
        <IconButton size="small" onClick={closePanel} sx={{ color: 'inherit' }} aria-label="챗봇 닫기">
          <CloseIcon fontSize="small" />
        </IconButton>
      </Stack>

      <Box sx={{ flex: 1, overflowY: 'auto', px: 2, py: 2, bgcolor: 'background.default' }}>
        <Stack spacing={1.5}>
          {messages.length === 0 && (
            <Typography variant="body2" color="text.secondary">
              {GREETING}
            </Typography>
          )}

          {visible.map((message, index) => (
            <Bubble
              key={index}
              role={message.role}
              content={message.content}
              imageUrl={message.imageUrl}
            />
          ))}

          {activeTool && (
            <Chip
              size="small"
              icon={<CircularProgress size={12} thickness={6} />}
              label={TOOL_LABELS[activeTool] ?? '조회 중'}
              sx={{ alignSelf: 'flex-start' }}
            />
          )}

          {error && <Alert severity="error">{error}</Alert>}
          <div ref={bottomRef} />
        </Stack>
      </Box>

      <Box sx={{ borderTop: 1, borderColor: 'divider' }}>
        {image && (
          <Stack direction="row" alignItems="center" spacing={1} sx={{ px: 1.5, pt: 1.5 }}>
            <Box
              component="img"
              src={image.previewUrl}
              alt="첨부한 이미지"
              sx={{ width: 48, height: 48, objectFit: 'cover', borderRadius: 1 }}
            />
            <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>
              이 사진으로 비슷한 공동구매를 찾습니다
            </Typography>
            <IconButton size="small" onClick={() => setImage(null)} aria-label="이미지 제거">
              <CloseIcon fontSize="small" />
            </IconButton>
          </Stack>
        )}

        <Stack direction="row" spacing={1} sx={{ p: 1.5 }}>
          <input
            ref={fileRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            hidden
            onChange={pickImage}
          />
          <IconButton
            onClick={() => fileRef.current?.click()}
            disabled={busy}
            aria-label="사진 첨부"
          >
            <ImageOutlinedIcon />
          </IconButton>
          <TextField
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={image ? '사진에 대해 물어보세요 (생략 가능)' : '메시지를 입력하세요'}
            size="small"
            fullWidth
            multiline
            maxRows={3}
            disabled={busy}
            // onKeyDown은 TextField가 아니라 실제 textarea에 붙여야 한다.
            inputProps={{ 'aria-label': '챗봇 메시지 입력', onKeyDown: handleKeyDown }}
          />
          <IconButton
            color="primary"
            onClick={send}
            disabled={busy || (!input.trim() && !image)}
            aria-label="보내기"
          >
            {busy ? <CircularProgress size={20} /> : <SendIcon />}
          </IconButton>
        </Stack>
      </Box>
    </Paper>
  )
}

function Bubble({ role, content, imageUrl }) {
  const isUser = role === 'user'

  if (!content && !imageUrl) {
    return (
      <Box sx={{ alignSelf: 'flex-start', px: 1 }}>
        <CircularProgress size={16} />
      </Box>
    )
  }

  return (
    <Box
      sx={{
        alignSelf: isUser ? 'flex-end' : 'flex-start',
        maxWidth: '85%',
        px: 1.5,
        py: 1,
        borderRadius: 2,
        whiteSpace: 'pre-wrap',
        bgcolor: isUser ? 'primary.main' : 'background.paper',
        color: isUser ? 'primary.contrastText' : 'text.primary',
        border: isUser ? 'none' : 1,
        borderColor: 'divider',
      }}
    >
      {imageUrl && (
        <Box
          component="img"
          src={imageUrl}
          alt="보낸 이미지"
          sx={{ display: 'block', maxWidth: '100%', borderRadius: 1, mb: content ? 0.75 : 0 }}
        />
      )}
      {content && <Typography variant="body2">{content}</Typography>}
    </Box>
  )
}
