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
    INVALID_STUDENT_NUMBER(HttpStatus.BAD_REQUEST, "학번은 1000~3999 범위여야 합니다."),
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 학생입니다."),
    STUDENT_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 학번입니다."),
    TEACHER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 교직원입니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다."),
    MEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 급식 정보입니다."),
    MEAL_ALREADY_EXISTS(HttpStatus.CONFLICT, "해당 날짜의 급식 정보가 이미 존재합니다."),
    CLUB_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 동아리입니다."),
    ROOM_ALREADY_IN_USE(HttpStatus.BAD_REQUEST, "다른 동아리가 이미 사용 중인 교실입니다."),
    MANAGER_NOT_IN_MEMBER(HttpStatus.BAD_REQUEST, "팀장은 구성원에 포함돼야 합니다."),
    STUDENT_ALREADY_IN_CLUB(HttpStatus.CONFLICT, "이미 다른 동아리에 속한 학생입니다."),
    TEACHER_ALREADY_IN_CLUB(HttpStatus.CONFLICT, "이미 다른 동아리를 담당하는 교직원입니다."),
    SUPERVISION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 자습 감독 정보입니다."),
    SUPERVISION_ALREADY_EXISTS(HttpStatus.CONFLICT, "해당 날짜의 자습 감독 정보가 이미 존재합니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "동시에 수정되어 처리하지 못했습니다. 다시 시도해 주세요."),
}
