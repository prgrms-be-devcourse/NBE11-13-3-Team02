package com.gachisa.queue.api

import com.gachisa.queue.core.QueueException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class QueueErrorResponse(val error: String, val message: String, val status: Int)

/**
 * 에러 코드 이름을 본문에 명시적으로 싣는다.
 *
 * Spring 기본 에러 포맷은 ResponseStatusException의 reason을 본문에 넣지 않아
 * core가 어떤 실패인지 구분할 수 없다. 서비스 간 계약이므로 직접 만든다.
 */
@RestControllerAdvice
class QueueExceptionHandler {

    @ExceptionHandler(QueueException::class)
    fun handle(exception: QueueException): ResponseEntity<QueueErrorResponse> {
        val error = exception.error
        return ResponseEntity.status(error.status)
            .body(QueueErrorResponse(error.name, error.message, error.status.value()))
    }
}
