package app.xquare.xquareopenapi.domain.auth.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

// 이메일은 받지 않고 이메일 인증 토큰에 저장된 값을 쓴다.
data class PasswordResetRequest(
    @field:NotBlank
    val emailVerifiedToken: String,

    @field:Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
    val newPassword: String,
)
