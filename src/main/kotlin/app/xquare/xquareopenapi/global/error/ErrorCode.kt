package app.xquare.xquareopenapi.global.error

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 완료되지 않았습니다."),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 없거나 만료 또는 폐기되었습니다. 재로그인이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 없거나 유효하지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "API 키가 없거나 유효하지 않습니다."),
    INVALID_STUDENT_NUMBER(HttpStatus.BAD_REQUEST, "학번은 1000~3999 범위여야 합니다."),
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 학생입니다."),
    STUDENT_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 학번입니다."),
    TEACHER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 교직원입니다."),
    API_KEY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 발급받은 API 키가 있습니다. 재발급을 이용해 주세요."),
    API_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "발급받은 API 키가 없습니다. 활용신청을 먼저 해 주세요."),
}
