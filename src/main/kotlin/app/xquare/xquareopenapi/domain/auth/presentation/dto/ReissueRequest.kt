package app.xquare.xquareopenapi.domain.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(requiredProperties = ["refreshToken"])
data class ReissueRequest(
    @field:NotBlank
    @field:Schema(description = "로그인 시 발급받은 리프레시 토큰", example = "eyJhbGci...")
    val refreshToken: String,
)
