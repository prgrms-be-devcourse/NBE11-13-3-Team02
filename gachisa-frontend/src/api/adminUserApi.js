import axiosInstance from './axiosInstance'

export const getUsers = ({ email, role, status, page = 0, size = 20 } = {}) =>
  axiosInstance.get('/admin/users', { params: { email, role, status, page, size } })

export const getUser = (userId) => axiosInstance.get(`/admin/users/${userId}`)

export const suspendUser = (userId) => axiosInstance.patch(`/admin/users/${userId}/suspend`)

export const reinstateUser = (userId) => axiosInstance.patch(`/admin/users/${userId}/reinstate`)

export const withdrawUser = (userId) => axiosInstance.patch(`/admin/users/${userId}/withdraw`)
