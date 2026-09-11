import { getGroupBuyList } from '../api/groupBuyApi'

// 상품 상세에서 "진행중인 공동구매로 바로 이동"을 지원하기 위한 조회.
// GroupBuyResponse에 productId가 내려오므로 이걸로 매칭한다(상품명 매칭은 동명 상품이 있으면 틀릴 수 있어 사용 안 함).
export async function fetchActiveGroupBuyIdsByProductId() {
  const { data } = await getGroupBuyList({ status: 'RECRUITING', page: 0, size: 100 })
  const map = {}
  ;(data?.content ?? []).forEach((gb) => {
    if (!(gb.productId in map)) map[gb.productId] = gb.groupBuyId
  })
  return map
}
