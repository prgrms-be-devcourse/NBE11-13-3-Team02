const KAKAO_CLIENT_ID = import.meta.env.VITE_KAKAO_CLIENT_ID
const NAVER_CLIENT_ID = import.meta.env.VITE_NAVER_CLIENT_ID

export const KAKAO_REDIRECT_URI = `${window.location.origin}/oauth/kakao`
export const NAVER_REDIRECT_URI = `${window.location.origin}/oauth/naver`

const NAVER_STATE_KEY = 'naver_oauth_state'

export const isKakaoLoginEnabled = () => Boolean(KAKAO_CLIENT_ID)
export const isNaverLoginEnabled = () => Boolean(NAVER_CLIENT_ID)

export function buildKakaoAuthUrl() {
  const params = new URLSearchParams({
    client_id: KAKAO_CLIENT_ID,
    redirect_uri: KAKAO_REDIRECT_URI,
    response_type: 'code',
  })
  return `https://kauth.kakao.com/oauth/authorize?${params.toString()}`
}

// 네이버는 CSRF 방지를 위해 state 파라미터를 요구한다.
// 요청 시 생성한 state를 세션에 저장해두고, 콜백에서 되돌아온 state와 일치하는지 검증한다.
export function buildNaverAuthUrl() {
  const state = crypto.randomUUID()
  sessionStorage.setItem(NAVER_STATE_KEY, state)
  const params = new URLSearchParams({
    client_id: NAVER_CLIENT_ID,
    redirect_uri: NAVER_REDIRECT_URI,
    response_type: 'code',
    state,
  })
  return `https://nid.naver.com/oauth2.0/authorize?${params.toString()}`
}

export function consumeNaverState() {
  const state = sessionStorage.getItem(NAVER_STATE_KEY)
  sessionStorage.removeItem(NAVER_STATE_KEY)
  return state
}
