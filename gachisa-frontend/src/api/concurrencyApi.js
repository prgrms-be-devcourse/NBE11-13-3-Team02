import axiosInstance from './axiosInstance'

/** local 프로필 전용: 공동구매 정원 동시성 스트레스 테스트 */
export const runConcurrencyStress = (groupBuyId, { mode, threadCount, quantityPerRequest } = {}) =>
    axiosInstance.post(`/dev/group-buys/${groupBuyId}/concurrency-stress`, {
        mode,
        threadCount,
        quantityPerRequest,
    })

/** local 프로필 전용: 동시성 데모에서 고를 수 있는 공동구매 목록 조회 */
export const getDevGroupBuyList = () => axiosInstance.get('/dev/group-buys')