package app.xquare.xquareopenapi.domain.auth.presentation.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Pattern(regexp = "^[A-Za-z0-9_-]{4,15}$", message = "4~15자 영문/숫자/하이픈/언더스코어만 사용할 수 있습니다.")
    val username: String,

    // BCrypt는 72바이트까지만 받으므로 1바이트 문자(공백 제외 ASCII)로 제한한다.
    @field:Pattern(regexp = "^(?=.*[^A-Za-z0-9])[\\x21-\\x7E]{8,20}$", message = "8~20자 영문/숫자/특수문자이며 특수문자를 포함해야 합니다.")
    val password: String,

    @field:Min(1000)
    @field:Max(3999)
    val studentNumber: Int,

    @field:NotBlank
    @field:Size(max = 10)
    val name: String,

    // OTP API와 같은 규칙으로 검사한다(앞뒤 공백 제거·소문자화 후 로컬 파트 허용 목록). 저장은 SignupUseCase가 정규화한 값으로 한다.
    @field:SchoolEmail
    val email: String,

    @field:NotBlank
    val emailVerifiedToken: String,
)
