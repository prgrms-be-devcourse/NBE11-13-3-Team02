import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import LoadingScreen from './LoadingScreen.jsx'

// roles: 허용할 role 문자열 배열 (예: ['ROLE_SELLER', 'ROLE_ADMIN'])
// ADMIN은 어떤 역할 제한 페이지든 항상 접근 가능하다.
export default function RoleRoute({ roles }) {
  const { user, initializing } = useAuth()

  if (initializing) return <LoadingScreen />
  if (user?.role !== 'ROLE_ADMIN' && !roles.includes(user?.role)) return <Navigate to="/403" replace />

  return <Outlet />
}
