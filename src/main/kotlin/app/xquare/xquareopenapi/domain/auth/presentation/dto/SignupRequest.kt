package app.xquare.xquareopenapi.domain.auth.presentation.dto

import jakarta.validation.constraints.Email
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

    @field:Email
    @field:Pattern(regexp = "^[^@\\s]+@dsm\\.hs\\.kr$", message = "@dsm.hs.kr 이메일만 사용할 수 있습니다.")
    val email: String,

    @field:NotBlank
    val emailVerifiedToken: String,
)
