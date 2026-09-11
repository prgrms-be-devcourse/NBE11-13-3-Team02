import axiosInstance from './axiosInstance.js'

export const getMyOrders = ({ page = 0, size = 20 } = {}) =>
  axiosInstance.get('/orders', { params: { page, size } })

export const getMyOrder = (orderId) => axiosInstance.get(`/orders/${orderId}`)

export const getMyOrderByParticipation = (participationId) =>
  axiosInstance.get(`/orders/by-participation/${participationId}`)

export const registerDeliveryAddress = (orderId, request) =>
  axiosInstance.post(`/orders/${orderId}/delivery-address`, request)

export const getDelivery = (orderId) =>
  axiosInstance.get(`/orders/${orderId}/delivery`)

export const updateDeliveryStatusByAdmin = (orderNumber, deliveryStatus) =>
  axiosInstance.patch(`/admin/orders/${orderNumber}/delivery-status`, { deliveryStatus })
