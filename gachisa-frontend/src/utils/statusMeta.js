// 백엔드가 status를 enum 코드가 아니라 이미 한글 라벨(GroupBuyStatus.getLabel() 등)로 내려주므로
// 라벨 문자열을 그대로 key로 사용한다.
export const GROUP_BUY_STATUS = {
  모집중: { label: '모집중', color: 'primary' },
  목표달성: { label: '목표달성', color: 'success' },
  목표미달: { label: '목표미달', color: 'default' },
  정산완료: { label: '정산완료', color: 'success' },
  취소됨: { label: '취소됨', color: 'error' },
}

export const PARTICIPATION_STATUS = {
  참여중: { label: '참여중', color: 'primary' },
  확정: { label: '확정', color: 'success' },
  환불됨: { label: '환불됨', color: 'default' },
  취소됨: { label: '취소됨', color: 'error' },
}

export const DELIVERY_STATUS = {
  WAITING_FOR_GROUP_BUY: { label: '공동구매 마감 대기', color: 'default' },
  PREPARING: { label: '상품 준비 중', color: 'warning' },
  SHIPPING: { label: '배송중', color: 'primary' },
  DELIVERED: { label: '배송완료', color: 'success' },
  CANCELLED: { label: '주문취소', color: 'error' },
  RETURNING: { label: '반품중', color: 'warning' },
  RETURNED: { label: '반품완료', color: 'default' },
}

export const PAYMENT_STATUS = {
  READY: { label: '결제대기', color: 'default' },
  PAID: { label: '결제완료', color: 'success' },
  REFUNDED: { label: '환불됨', color: 'error' },
}

export const PRODUCT_STATUS = {
  ON_SALE: { label: '판매중', color: 'primary' },
  SUSPENDED: { label: '판매중지', color: 'default' },
}

export function statusMeta(map, status) {
  return map[status] ?? { label: status ?? '-', color: 'default' }
}

export const formatPrice = (value) =>
  typeof value === 'number' ? `${value.toLocaleString('ko-KR')}원` : '-'

export const formatDateTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
