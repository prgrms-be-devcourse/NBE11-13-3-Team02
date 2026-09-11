import axiosInstance from './axiosInstance.js'

const PATH = '/users/me/delivery-addresses'

export const getSavedDeliveryAddresses = () => axiosInstance.get(PATH)
export const createSavedDeliveryAddress = (request) => axiosInstance.post(PATH, request)
export const updateSavedDeliveryAddress = (id, request) => axiosInstance.patch(`${PATH}/${id}`, request)
export const deleteSavedDeliveryAddress = (id) => axiosInstance.delete(`${PATH}/${id}`)
