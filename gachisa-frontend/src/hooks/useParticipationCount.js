import { useEffect, useState } from 'react'
import { getParticipationCount } from '../api/participationApi'

/**
 * PT-03. 실시간 참여 인원을 주기적으로 폴링한다.
 * Redis 캐시 없이 DB를 직접 조회하는 가벼운 전용 엔드포인트를 사용하므로,
 * 폴링 주기를 너무 짧게 잡지 않도록 주의 (기본 5초).
 */
export function useParticipationCount(groupBuyId, intervalMs = 5000) {
  const [count, setCount] = useState(null)

  useEffect(() => {
    if (!groupBuyId) return

    let cancelled = false

    const fetchCount = async () => {
      try {
        const { data } = await getParticipationCount(groupBuyId)
        if (!cancelled) setCount(data)
      } catch (e) {
        // 폴링 실패는 조용히 무시 (다음 주기에 재시도)
      }
    }

    fetchCount()
    const timer = setInterval(fetchCount, intervalMs)

    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [groupBuyId, intervalMs])

  return count
}
