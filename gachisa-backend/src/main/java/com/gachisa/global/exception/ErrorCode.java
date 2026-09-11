package com.gachisa.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),

    // GroupBuy
    GROUP_BUY_NOT_FOUND(HttpStatus.NOT_FOUND, "공동구매를 찾을 수 없습니다."),
    GROUP_BUY_FULL(HttpStatus.CONFLICT, "정원이 마감되었습니다."),
    GROUP_BUY_CLOSED(HttpStatus.CONFLICT, "이미 마감되었거나 모집중이 아닌 공동구매입니다."),
    GROUP_BUY_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "마감시각은 모집시작시각 이후여야 합니다."),
    GROUP_BUY_CANNOT_CANCEL(HttpStatus.CONFLICT, "이미 마감/정산된 공동구매는 취소할 수 없습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 상태 전이입니다."),

    // Participation
    PARTICIPATION_NOT_FOUND(HttpStatus.NOT_FOUND, "참여 내역을 찾을 수 없습니다."),
    PARTICIPATION_NOT_CANCELABLE(HttpStatus.CONFLICT, "공동구매 마감 전 참여중 상태에서만 취소할 수 있습니다. 마감 또는 확정 이후에는 환불을 이용하세요."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "참여 수량은 1 이상이어야 합니다."),

    // Queue
    QUEUE_NOT_OPEN(HttpStatus.CONFLICT, "현재 대기열에 참여할 수 없습니다."),
    QUEUE_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 대기열 토큰입니다."),
    QUEUE_ADMISSION_REQUIRED(HttpStatus.CONFLICT, "결제 차례가 아직 도착하지 않았습니다."),
    QUEUE_ADMISSION_EXPIRED(HttpStatus.CONFLICT, "결제 가능 시간이 만료되어 대기열 끝으로 이동했습니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다."),
    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 생성된 결제가 있습니다."),
    PAYMENT_IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "멱등키는 UUID v4 형식이어야 합니다."),
    PAYMENT_IDEMPOTENCY_KEY_CONFLICT(HttpStatus.UNPROCESSABLE_CONTENT,
            "동일한 멱등키로 다른 결제를 요청할 수 없습니다."),
    PAYMENT_NOT_ALLOWED(HttpStatus.CONFLICT, "결제할 수 없는 참여 상태입니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 결제입니다."),
    PAYMENT_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 시도 내역을 찾을 수 없습니다."),
    PAYMENT_ATTEMPT_IN_PROGRESS(HttpStatus.CONFLICT, "이전 결제 시도를 확인하고 있습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),
    PAYMENT_ORDER_MISMATCH(HttpStatus.BAD_REQUEST, "PG 주문번호가 일치하지 않습니다."),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT, "결제 가능 시간이 만료되었습니다."),
    PAYMENT_GATEWAY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "PG 테스트 키가 설정되지 않았습니다."),
    PAYMENT_GATEWAY_REJECTED(HttpStatus.BAD_GATEWAY, "PG사가 결제 승인을 거절했습니다."),
    PAYMENT_GATEWAY_PROCESSING(HttpStatus.CONFLICT, "PG사가 이전 결제 요청을 처리하고 있습니다."),
    PAYMENT_GATEWAY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PG사에 연결할 수 없습니다."),
    PAYMENT_GATEWAY_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "PG사 응답 정보가 올바르지 않습니다."),
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "환불 내역을 찾을 수 없습니다."),
    REFUND_NOT_ALLOWED(HttpStatus.CONFLICT, "환불할 수 없는 결제 상태입니다."),
    REFUND_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "환불 사유가 필요합니다."),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    DELIVERY_ADDRESS_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 배송지가 등록된 주문입니다."),
    DELIVERY_ADDRESS_REQUIRED(HttpStatus.CONFLICT, "배송지 등록이 필요한 주문입니다."),
    DELIVERY_ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "저장된 배송지를 찾을 수 없습니다."),
    INVALID_DELIVERY_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 배송 상태 변경입니다."),

    // Product
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 등록한 상품명입니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),

    // Image
    IMAGE_EMPTY(HttpStatus.BAD_REQUEST, "이미지 파일이 비어있습니다."),
    IMAGE_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "이미지 파일(jpg, png, webp, gif)만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),

    // Category
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 카테고리명입니다."),
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "하위 카테고리가 존재하여 삭제할 수 없습니다."),

    // Auth
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "인증 정보를 찾을 수 없습니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "보안을 위해 모든 기기에서 로그아웃되었습니다. 다시 로그인해주세요."),
    INVALID_SIGNUP_ROLE(HttpStatus.BAD_REQUEST, "회원가입 시 선택할 수 없는 권한입니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "소셜 로그인 제공자와 통신 중 오류가 발생했습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
