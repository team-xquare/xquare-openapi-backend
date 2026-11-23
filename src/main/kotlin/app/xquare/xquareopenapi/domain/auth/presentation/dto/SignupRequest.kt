package app.xquare.xquareopenapi.domain.auth.presentation.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Pattern(regexp = "^[A-Za-z0-9_-]{4,15}$", message = "4~15자 영문/숫자/하이픈/언더스코어만 사용할 수 있습니다.")
    val username: String,

    @field:Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
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
