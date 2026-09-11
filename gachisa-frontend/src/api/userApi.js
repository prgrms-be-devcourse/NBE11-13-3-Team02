import axiosInstance from './axiosInstance'

export const getMe = () => axiosInstance.get('/users/me')

export const updateMe = ({ name, currentPassword, newPassword }) =>
  axiosInstance.patch('/users/me', { name, currentPassword, newPassword })
