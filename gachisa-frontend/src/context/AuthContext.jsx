import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import * as authApi from '../api/authApi'
import * as userApi from '../api/userApi'
import { reissueAccessToken } from '../api/axiosInstance'
import { setAccessToken, clearAccessToken } from '../api/tokenStore'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [initializing, setInitializing] = useState(true)

  const fetchAndSetUser = useCallback(async () => {
    const { data } = await userApi.getMe()
    setUser(data)
    return data
  }, [])

  useEffect(() => {
    // 앱 진입 시 httpOnly 리프레시 쿠키(sid)로 세션 복원 시도
    let cancelled = false
    ;(async () => {
      try {
        await reissueAccessToken()
        if (!cancelled) await fetchAndSetUser()
      } catch {
        if (!cancelled) setUser(null)
      } finally {
        if (!cancelled) setInitializing(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [fetchAndSetUser])

  useEffect(() => {
    const handleExpired = () => setUser(null)
    window.addEventListener('auth:sessionExpired', handleExpired)
    return () => window.removeEventListener('auth:sessionExpired', handleExpired)
  }, [])

  const login = useCallback(
    async (email, password) => {
      const { data } = await authApi.login(email, password)
      setAccessToken(data.accessToken)
      await fetchAndSetUser()
    },
    [fetchAndSetUser],
  )

  const loginWithKakao = useCallback(
    async (code, redirectUri) => {
      const { data } = await authApi.loginWithKakao(code, redirectUri)
      setAccessToken(data.accessToken)
      await fetchAndSetUser()
    },
    [fetchAndSetUser],
  )

  const loginWithNaver = useCallback(
    async (code, redirectUri, state) => {
      const { data } = await authApi.loginWithNaver(code, redirectUri, state)
      setAccessToken(data.accessToken)
      await fetchAndSetUser()
    },
    [fetchAndSetUser],
  )

  const signUp = useCallback((payload) => authApi.signUp(payload), [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      clearAccessToken()
      setUser(null)
    }
  }, [])

  const value = useMemo(
    () => ({
      user,
      initializing,
      isAuthenticated: !!user,
      isBuyer: user?.role === 'ROLE_BUYER',
      isSeller: user?.role === 'ROLE_SELLER',
      isAdmin: user?.role === 'ROLE_ADMIN',
      login,
      loginWithKakao,
      loginWithNaver,
      signUp,
      logout,
      refreshUser: fetchAndSetUser,
    }),
    [user, initializing, login, loginWithKakao, loginWithNaver, signUp, logout, fetchAndSetUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
