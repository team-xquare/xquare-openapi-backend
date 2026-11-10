package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException

// 메일 서버는 로컬 파트의 대소문자를 구분하지 않으므로 소문자로 맞춘다.
// OTP·발송 제한 키, 이메일 인증 토큰, 회원가입 비교와 저장에 모두 이 값을 쓴다.
internal fun normalizeEmail(email: String): String = email.trim().lowercase()

// 로컬 파트는 허용 목록(영문 소문자·숫자·`.`·`_`·`-`)만 받는다. `+태그`, 따옴표(`"victim"@`), 역슬래시, 괄호 주석처럼
// 같은 메일함을 가리키는 다른 문자열을 만들 수 있는 문자는 모두 막아, 이메일별 제한과 가입 중복 검사를 우회하지 못하게 한다.
// 점은 앞뒤에 오거나 연달아 올 수 없다(따옴표 없는 주소에서 허용되지 않는 형태).
private val SCHOOL_EMAIL = Regex("""^[a-z0-9_-]+(\.[a-z0-9_-]+)*@dsm\.hs\.kr$""")

// 정규화(앞뒤 공백 제거·소문자화)한 뒤 검사한다. OTP 발송·검증과 회원가입이 같은 규칙을 쓴다.
// 검사한 값(정규화한 값)을 그대로 키와 저장값으로 쓰므로, 통과한 이메일에는 ASCII 허용 문자만 들어 있다.
internal fun isSchoolEmail(email: String): Boolean = SCHOOL_EMAIL.matches(normalizeEmail(email))

internal fun normalizeOtpEmail(email: String): String {
    if (!isSchoolEmail(email)) {
        throw XquareException(ErrorCode.INVALID_EMAIL_FORMAT)
    }
    return normalizeEmail(email)
}
