package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException

// 메일 서버는 로컬 파트의 대소문자를 구분하지 않으므로 소문자로 맞춘다.
// OTP·발송 제한 키, 이메일 인증 토큰, 회원가입 비교와 저장에 모두 이 값을 쓴다.
internal fun normalizeEmail(email: String): String = email.trim().lowercase()

// `+태그`는 같은 메일함으로 배달되어 이메일별 제한을 우회할 수 있으므로 형식 오류로 거부한다.
internal fun normalizeOtpEmail(email: String): String {
    val normalized = normalizeEmail(email)
    if (!Regex("""^[^@\s+]+@dsm\.hs\.kr$""").matches(normalized)) {
        throw XquareException(ErrorCode.INVALID_EMAIL_FORMAT)
    }
    return normalized
}
