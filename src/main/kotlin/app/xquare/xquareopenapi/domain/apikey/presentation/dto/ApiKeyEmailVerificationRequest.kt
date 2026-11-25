package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import jakarta.validation.constraints.NotBlank

// 키 재발급·갱신 요청. 이메일 인증 토큰만 받는다.
data class ApiKeyEmailVerificationRequest(
    // POST /auth/email/otp/verify가 준 토큰. 로그인한 사용자의 이메일로 받은 것이어야 한다.
    @field:NotBlank
    val emailVerifiedToken: String,
)
