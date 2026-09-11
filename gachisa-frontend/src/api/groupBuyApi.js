import axiosInstance from './axiosInstance'

// GB-02. 목록 조회
export const getGroupBuyList = ({ status, page = 0, size = 20 } = {}) =>
  axiosInstance.get('/group-buys', { params: { status, page, size } })

// 공동구매 검색/필터 (키워드/카테고리/가격/정렬) - 파라미터가 없으면 목록 조회와 동일하게 동작한다.
export const searchGroupBuys = ({
  status,
  keyword,
  categoryId,
  minPrice,
  maxPrice,
  sort,
  page = 0,
  size = 20,
} = {}) =>
  axiosInstance.get('/group-buys/search', {
    params: { status, keyword, categoryId, minPrice, maxPrice, sort, page, size },
  })

// GB-03. 상세 조회
export const getGroupBuyDetail = (groupBuyId) =>
  axiosInstance.get(`/group-buys/${groupBuyId}`)

// GB-01. 생성 (판매자)
export const createGroupBuy = (payload) =>
  axiosInstance.post('/group-buys', payload)

// GB-05. 취소 (판매자)
export const cancelGroupBuy = (groupBuyId) =>
  axiosInstance.patch(`/group-buys/${groupBuyId}/cancel`)
