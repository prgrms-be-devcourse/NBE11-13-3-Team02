import axiosInstance from './axiosInstance'

export const signUp = ({ email, password, name, role }) =>
  axiosInstance.post('/auth/signup', { email, password, name, role })

export const login = (email, password) =>
  axiosInstance.post('/auth/login', { email, password })

export const logout = () => axiosInstance.post('/auth/logout')

export const loginWithKakao = (code, redirectUri) =>
  axiosInstance.post('/auth/oauth/kakao', { code, redirectUri })

export const loginWithNaver = (code, redirectUri, state) =>
  axiosInstance.post('/auth/oauth/naver', { code, redirectUri, state })
