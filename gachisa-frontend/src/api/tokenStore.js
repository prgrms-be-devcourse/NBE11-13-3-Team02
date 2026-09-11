// accessToken은 XSS로 인한 탈취 위험을 줄이기 위해 localStorage가 아닌 메모리에만 보관한다.
// 새로고침 시에는 httpOnly 쿠키(sid)를 이용한 /auth/reissue 호출로 세션을 복원한다.
let accessToken = null
const listeners = new Set()

export function getAccessToken() {
  return accessToken
}

export function setAccessToken(token) {
  accessToken = token
  listeners.forEach((listener) => listener(accessToken))
}

export function clearAccessToken() {
  setAccessToken(null)
}

export function subscribe(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
