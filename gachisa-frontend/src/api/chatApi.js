import { getAccessToken } from './tokenStore'

// EventSource는 Authorization 헤더를 붙일 수 없고 POST도 못 한다.
// accessToken이 메모리에만 있는 구조라 fetch + ReadableStream으로 SSE를 직접 읽는다.
export async function streamChat({ message, history = [], conversationId = null, image = null, signal, onEvent }) {
  const response = await fetch('/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getAccessToken()}`,
    },
    body: JSON.stringify({ message, history, conversationId, image }),
    signal,
  })

  if (!response.ok) {
    const error = new Error(`챗봇 서버 응답 오류: ${response.status}`)
    error.status = response.status
    throw error
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // SSE 이벤트는 빈 줄로 구분된다. 청크 경계가 이벤트 중간을 자를 수 있어 버퍼에 모은다.
    let boundary
    while ((boundary = buffer.indexOf('\n\n')) !== -1) {
      const block = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const parsed = parseEventBlock(block)
      if (parsed) onEvent(parsed.event, parsed.data)
    }
  }
}

function parseEventBlock(block) {
  let event = 'message'
  let raw = ''

  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) raw += line.slice(5).trim()
  }

  if (!raw) return null
  try {
    return { event, data: JSON.parse(raw) }
  } catch {
    return null
  }
}
