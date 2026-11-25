package app.xquare.xquareopenapi.domain.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

// 이메일은 받지 않고 이메일 인증 토큰에 저장된 값을 쓴다.
@Schema(requiredProperties = ["emailVerifiedToken", "newPassword"])
data class PasswordResetRequest(
    @field:NotBlank
    @field:Schema(description = "이메일 OTP 검증으로 받은 인증 완료 토큰(600초 유효, 1회용)", example = "3f2a9c1e-7b4d-4e8a-9c2f-1d5e6a7b8c9d")
    val emailVerifiedToken: String,

    @field:Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
    @field:Schema(description = "새 비밀번호(8~20자, 특수문자 포함, 공백 제외 ASCII)", minLength = 8, maxLength = 20, example = "NewPassword1!")
    val newPassword: String,
)
