package app.xquare.xquareopenapi.domain.auth.presentation.dto

// 회원가입과 비밀번호 재설정이 같은 규칙을 쓴다.
// BCrypt는 72바이트까지만 받으므로 1바이트 문자(공백 제외 ASCII)로 제한한다.
const val PASSWORD_PATTERN = "^(?=.*[^A-Za-z0-9])[\\x21-\\x7E]{8,20}$"
const val PASSWORD_MESSAGE = "8~20자 영문/숫자/특수문자이며 특수문자를 포함해야 합니다."
