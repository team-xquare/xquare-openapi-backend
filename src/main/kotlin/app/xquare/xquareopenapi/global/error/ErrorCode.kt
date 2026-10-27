package app.xquare.xquareopenapi.global.error

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "@dsm.hs.kr 형식이 아닙니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "메일 발송 또는 인증코드 저장에 실패했습니다."),
    OTP_NOT_FOUND(HttpStatus.BAD_REQUEST, "발송된 인증코드가 없거나 만료되었습니다. 재발송이 필요합니다."),
    OTP_MISMATCH(HttpStatus.BAD_REQUEST, "인증코드가 일치하지 않습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 완료되지 않았습니다."),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 없거나 만료 또는 폐기되었습니다. 재로그인이 필요합니다."),
}
