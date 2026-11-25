package app.xquare.xquareopenapi.domain.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["emailVerifiedToken"])
data class EmailVerifiedTokenResponse(
    @field:Schema(description = "이메일 인증 완료 토큰(600초 유효)", example = "3f2a9c1e-7b4d-4e8a-9c2f-1d5e6a7b8c9d")
    val emailVerifiedToken: String,
)
