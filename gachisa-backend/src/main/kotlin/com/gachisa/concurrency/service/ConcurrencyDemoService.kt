package com.gachisa.concurrency.service

import com.gachisa.concurrency.dto.ConcurrencyStressRequest
import com.gachisa.concurrency.dto.ConcurrencyStressRequest.Mode
import com.gachisa.concurrency.dto.ConcurrencyStressResponse
import com.gachisa.concurrency.dto.GroupBuyListItem
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.groupbuy.repository.GroupBuyRepository
import com.gachisa.groupbuy.repository.GroupBuyStockRedisRepository
import com.gachisa.groupbuy.service.GroupBuyService
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * 공동구매 정원 동시성 문제를 재현/검증하기 위한 로컬 스트레스 도구.
 * 실제 참여(Participation) 레코드는 만들지 않고 currentCount 예약만 경쟁시킨다.
 *
 * 부하 생성에만 코루틴을 쓴다. 예약 로직 자체는 JPA/JDBC라 블로킹이며, suspend로
 * 바꿔도 커넥션을 쥔 채라 스레드를 반납할 수 없다. 여기서 얻는 것은 처리량이 아니라
 * 요청 수만큼 OS 스레드를 만들지 않는 것과, 출발선·완료 대기·타임아웃을 래치로 직접
 * 엮지 않아도 되는 것이다.
 */
@Service
@Profile("local")
class ConcurrencyDemoService(
    private val groupBuyRepository: GroupBuyRepository,
    private val stockRedisRepository: GroupBuyStockRedisRepository,
    private val groupBuyService: GroupBuyService,
    private val transactionManager: PlatformTransactionManager,
) {

    /**
     * 데모 페이지에서 groupBuyId를 직접 타이핑하지 않고 드롭다운으로 고를 수 있도록
     * DB에 저장된 GroupBuy 목록을 내려준다. (local 전용, 개발 편의용이라 페이징 없이 전체 조회)
     */
    @Transactional(readOnly = true)
    fun listGroupBuys(): List<GroupBuyListItem> =
        groupBuyRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).map { groupBuy ->
            GroupBuyListItem.builder()
                .id(groupBuy.id)
                .productName(groupBuy.product.name)
                .status(groupBuy.status)
                .currentCount(groupBuy.currentCount)
                .targetCount(groupBuy.targetCount)
                .build()
        }

    fun run(groupBuyId: Long, request: ConcurrencyStressRequest): ConcurrencyStressResponse {
        val snapshot = groupBuyRepository.findById(groupBuyId)
            .orElseThrow { CustomException(ErrorCode.GROUP_BUY_NOT_FOUND) }

        val originalCount = snapshot.currentCount
        val target = snapshot.targetCount
        // 이미 마감된 공동구매에 다시 때리면 전부 GROUP_BUY_FULL 이라 성공 0이 된다.
        // 데모는 currentCount를 0으로 되돌린 뒤 같은 정원을 경쟁시킨다.
        resetStock(groupBuyId, 0, target)

        // MVC 요청 스레드에서 호출되므로 여기서 블로킹으로 받는다. 부하가 끝나야
        // 결과를 집계할 수 있어 비동기로 넘길 이유도 없다.
        val outcomes = runBlocking { fireConcurrently(groupBuyId, request) }

        val success = outcomes.count { it == Outcome.SUCCESS }
        val rejectedAsFull = outcomes.count { it == Outcome.REJECTED_AS_FULL }
        val otherFailures = outcomes.count { it == Outcome.OTHER_FAILURE }

        val afterDb = groupBuyRepository.findById(groupBuyId)
            .orElseThrow { CustomException(ErrorCode.GROUP_BUY_NOT_FOUND) }
            .currentCount
        val afterRedis = stockRedisRepository.getCurrentCount(groupBuyId).orElse(null)

        // --- 오버셀 판정 ---
        // 1) DB currentCount 자체가 target을 넘은 경우 (원자적 UPDATE 방식이었다면 이렇게 드러남)
        // 2) DB값은 target 이하인데 successCount가 target을 넘은 경우
        //    → UNSAFE 모드는 read-modify-write로 currentCount를 "리터럴 값으로 덮어쓰기" 하기 때문에
        //      여러 트랜잭션이 같은 스냅샷(예: 0)을 읽고 각자 "1"을 계산해 커밋하면
        //      최종 DB값은 낮게 나오면서(Lost Update) 성공 응답만 target을 초과해서 발생한다.
        //      이 케이스가 실무에서 더 위험하다: 응답은 성공인데 실제 반영이 안 된 상태이기 때문.
        val dbOversold = afterDb > target
        val lostUpdate = !dbOversold && success > target

        val summary = when {
            dbOversold -> "동시성 문제 재현: DB currentCount($afterDb) > targetCount($target)"
            lostUpdate -> "동시성 문제 재현(Lost Update): 성공 응답 ${success}건이 정원 ${target}명을 초과했지만 " +
                "최종 DB currentCount는 ${afterDb}로 오히려 낮게 나타남 " +
                "(동시에 읽은 스냅샷을 각자 덮어써서 일부 반영분이 유실됨)"
            else -> "정원 준수: 성공 $success / 정원초과거절 $rejectedAsFull / 기타실패 $otherFailures (target $target)"
        }

        return ConcurrencyStressResponse.builder()
            .mode(request.mode)
            .groupBuyId(groupBuyId)
            .threadCount(request.threadCount)
            .quantityPerRequest(request.quantityPerRequest)
            .targetCount(target)
            .currentCountBefore(originalCount)
            .currentCountAfterDb(afterDb)
            .currentCountAfterRedis(afterRedis)
            .remainingSlotsAtStart(target)
            .successCount(success)
            .failureCount(rejectedAsFull + otherFailures)
            .rejectedAsFull(rejectedAsFull)
            .otherFailures(otherFailures)
            .oversold(dbOversold || lostUpdate)
            .summary(summary)
            .build()
    }

    /**
     * 요청 수만큼의 예약 시도를 동시에 출발시키고 결과만 모은다.
     *
     * 예약이 블로킹 JDBC라 병렬도를 명시해야 한다. 기본 Dispatchers.IO(64)에 맡기면
     * 그보다 많은 요청이 줄을 서서 경합이 약해지고, 데모가 재현하려는 상황이 흐려진다.
     */
    private suspend fun fireConcurrently(
        groupBuyId: Long,
        request: ConcurrencyStressRequest,
    ): List<Outcome> = coroutineScope {
        val dispatcher = Dispatchers.IO.limitedParallelism(request.threadCount)
        val atStartLine = CompletableDeferred<Unit>()
        val yetToArrive = AtomicInteger(request.threadCount)
        val startSignal = CompletableDeferred<Unit>()

        val attempts = List(request.threadCount) {
            async(dispatcher) {
                if (yetToArrive.decrementAndGet() == 0) {
                    atStartLine.complete(Unit)
                }
                startSignal.await()
                attempt(groupBuyId, request.quantityPerRequest, request.mode)
            }
        }

        withTimeoutOrNull(START_LINE_TIMEOUT_MILLIS) { atStartLine.await() }
            ?: throw CustomException(ErrorCode.INVALID_REQUEST)
        startSignal.complete(Unit)

        withTimeoutOrNull(RUN_TIMEOUT_MILLIS) { attempts.awaitAll() }
            ?: throw CustomException(ErrorCode.INVALID_REQUEST)
    }

    private fun attempt(groupBuyId: Long, quantity: Int, mode: Mode): Outcome =
        try {
            attemptReserve(groupBuyId, quantity, mode)
            Outcome.SUCCESS
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (unwrapCustomException(e)?.errorCode == ErrorCode.GROUP_BUY_FULL) {
                Outcome.REJECTED_AS_FULL
            } else {
                Outcome.OTHER_FAILURE
            }
        }

    private fun resetStock(groupBuyId: Long, currentCount: Int, targetCount: Int) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            groupBuyRepository.findByIdForUpdate(groupBuyId)
                .orElseThrow { CustomException(ErrorCode.GROUP_BUY_NOT_FOUND) }
                .resetCurrentCount(currentCount)
        }
        try {
            stockRedisRepository.overwrite(groupBuyId, currentCount, targetCount)
        } catch (ex: RuntimeException) {
            throw CustomException(ErrorCode.INVALID_REQUEST)
        }
    }

    private fun attemptReserve(groupBuyId: Long, quantity: Int, mode: Mode) {
        when (mode) {
            Mode.UNSAFE -> unsafeReserve(groupBuyId, quantity)
            Mode.DB_LOCK -> dbLockReserve(groupBuyId, quantity)
            Mode.REDIS_AND_DB -> groupBuyService.reserveSlots(groupBuyId, quantity)
        }
    }

    /** 의도적으로 락 없이 읽고 증가 — Lost Update / 초과 모집 재현용 */
    private fun unsafeReserve(groupBuyId: Long, quantity: Int) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            val groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow { CustomException(ErrorCode.GROUP_BUY_NOT_FOUND) }
            // 읽기와 쓰기 사이를 벌려 Lost Update가 안정적으로 재현되게 한다.
            Thread.sleep(5)
            groupBuy.reserve(quantity)
        }
    }

    private fun dbLockReserve(groupBuyId: Long, quantity: Int) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            groupBuyRepository.findByIdForUpdate(groupBuyId)
                .orElseThrow { CustomException(ErrorCode.GROUP_BUY_NOT_FOUND) }
                .reserve(quantity)
        }
    }

    private fun unwrapCustomException(ex: Throwable): CustomException? =
        generateSequence(ex) { it.cause }.filterIsInstance<CustomException>().firstOrNull()

    private enum class Outcome { SUCCESS, REJECTED_AS_FULL, OTHER_FAILURE }

    private companion object {
        const val START_LINE_TIMEOUT_MILLIS = 10_000L
        const val RUN_TIMEOUT_MILLIS = 60_000L
    }
}
