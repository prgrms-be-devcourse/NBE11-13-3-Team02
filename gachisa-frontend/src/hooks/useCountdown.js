import { useEffect, useState } from 'react'

/**
 * 초 단위 남은 시간을 1초마다 감소시켜주는 훅.
 * 서버가 응답한 remainingSeconds를 기준으로 클라이언트에서 째깍째깍 흐르게만 하고,
 * 진짜 마감 판정(정원 달성/미달)은 항상 서버 값을 신뢰한다 (여긴 표시용일 뿐).
 */
export function useCountdown(initialSeconds) {
  const [seconds, setSeconds] = useState(initialSeconds ?? 0)

  useEffect(() => {
    setSeconds(initialSeconds ?? 0)
  }, [initialSeconds])

  useEffect(() => {
    if (seconds <= 0) return
    const timer = setInterval(() => {
      setSeconds((prev) => Math.max(0, prev - 1))
    }, 1000)
    return () => clearInterval(timer)
  }, [seconds > 0])

  const format = () => {
    const d = Math.floor(seconds / 86400)
    const h = Math.floor((seconds % 86400) / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    const s = seconds % 60
    if (d > 0) return `${d}일 ${h}시간 남음`
    if (h > 0) return `${h}시간 ${m}분 남음`
    if (m > 0) return `${m}분 ${s}초 남음`
    if (seconds > 0) return `${s}초 남음`
    return '마감'
  }

  return { seconds, label: format() }
}
