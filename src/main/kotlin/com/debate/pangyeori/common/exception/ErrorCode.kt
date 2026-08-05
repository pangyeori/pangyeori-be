package com.debate.pangyeori.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    // 토론
    DEBATE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 토론입니다."),
    DEBATE_STAGE_CONFLICT(HttpStatus.UNPROCESSABLE_ENTITY, "현재 단계에서 허용되지 않는 작업입니다."),
    DEBATE_ALREADY_FINISHED(HttpStatus.UNPROCESSABLE_ENTITY, "이미 종료된 토론입니다."),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    UNAUTHORIZED_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.UNPROCESSABLE_ENTITY, "이메일 인증이 완료되지 않았습니다."),

    // 인증
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),

    // 이메일 인증
    EMAIL_VERIFICATION_CODE_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "인증 코드가 존재하지 않거나 만료되었습니다."),
    EMAIL_VERIFICATION_CODE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "인증 코드가 일치하지 않습니다."),
    EMAIL_VERIFICATION_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증이 완료된 이메일입니다."),
    EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    EXTERNAL_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "외부 서비스 오류가 발생했습니다."),
}
