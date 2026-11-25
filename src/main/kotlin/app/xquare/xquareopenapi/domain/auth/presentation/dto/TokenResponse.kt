package app.xquare.xquareopenapi.domain.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["accessToken", "refreshToken"])
data class TokenResponse(
    @field:Schema(description = "액세스 토큰(Authorization: Bearer {accessToken})", example = "eyJhbGci...")
    val accessToken: String,
    @field:Schema(description = "리프레시 토큰", example = "eyJhbGci...")
    val refreshToken: String,
)
