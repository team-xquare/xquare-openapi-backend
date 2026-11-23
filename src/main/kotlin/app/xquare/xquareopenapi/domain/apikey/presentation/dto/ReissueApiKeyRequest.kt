package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import jakarta.validation.constraints.NotBlank

data class ReissueApiKeyRequest(
    // POST /auth/email/otp/verify가 준 토큰. 로그인한 사용자의 이메일로 받은 것이어야 한다.
    @field:NotBlank
    val emailVerifiedToken: String,
)
