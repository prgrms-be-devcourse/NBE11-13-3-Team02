// JWT는 헤더.페이로드.서명 구조이며, 페이로드는 base64url로 인코딩된 JSON.
// 서버에 묻지 않고도 토큰 안의 클레임(userId, role 등)을 바로 읽기 위한 최소 유틸.
// (검증은 서버가 하므로, 프론트에서는 payload를 "신뢰"가 아니라 "표시 용도"로만 사용)
export function parseJwt(token) {
  try {
    const base64Payload = token.split('.')[1]
    const payload = base64Payload.replace(/-/g, '+').replace(/_/g, '/')
    const decoded = decodeURIComponent(
      atob(payload)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join('')
    )
    return JSON.parse(decoded)
  } catch {
    return null
  }
}
