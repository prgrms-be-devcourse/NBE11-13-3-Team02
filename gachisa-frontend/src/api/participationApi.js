import axiosInstance from './axiosInstance'

// PT-01. 참여
export const participate = (groupBuyId, quantity) =>
  axiosInstance.post(`/group-buys/${groupBuyId}/participations`, { quantity })

// PT-02. 참여 취소
export const cancelParticipation = (participationId) =>
  axiosInstance.post(`/participations/${participationId}/cancel`)

// PT-03. 실시간 참여 인원 조회 (폴링용)
export const getParticipationCount = (groupBuyId) =>
  axiosInstance.get(`/group-buys/${groupBuyId}/participation-count`)

// PT-04. 참여 이력 조회
export const getMyParticipations = ({ status, page = 0, size = 20 } = {}) =>
  axiosInstance.get('/users/me/participations', { params: { status, page, size } })
