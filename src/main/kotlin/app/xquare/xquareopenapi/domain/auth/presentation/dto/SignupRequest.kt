package app.xquare.xquareopenapi.domain.auth.presentation.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class SignupRequest(
    @field:Pattern(regexp = "^[A-Za-z0-9_-]{4,15}$", message = "4~15자 영문/숫자/하이픈/언더스코어만 사용할 수 있습니다.")
    val username: String,

    @field:Pattern(regexp = "^(?=.*[^A-Za-z0-9]).{8,20}$", message = "8~20자이며 특수문자를 포함해야 합니다.")
    val password: String,

    @field:Min(1000)
    @field:Max(3999)
    val studentNumber: Int,

    @field:NotBlank
    val name: String,

    @field:Pattern(regexp = "^[^@\\s]+@dsm\\.hs\\.kr$", message = "@dsm.hs.kr 이메일만 사용할 수 있습니다.")
    val email: String,

    @field:NotBlank
    val emailVerifiedToken: String,
)
