import axiosInstance from './axiosInstance'

export const getCategories = () => axiosInstance.get('/categories')

export const getCategory = (categoryId) => axiosInstance.get(`/categories/${categoryId}`)

// ADMIN 전용
export const createCategory = ({ name, parentId }) =>
  axiosInstance.post('/categories', { name, parentId })

// ADMIN 전용
export const updateCategory = (categoryId, { name }) =>
  axiosInstance.patch(`/categories/${categoryId}`, { name })

// ADMIN 전용
export const deleteCategory = (categoryId) => axiosInstance.delete(`/categories/${categoryId}`)
