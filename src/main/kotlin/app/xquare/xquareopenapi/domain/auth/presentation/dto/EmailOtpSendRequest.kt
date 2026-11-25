package app.xquare.xquareopenapi.domain.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["email"])
data class EmailOtpSendRequest(
    @field:Schema(description = "인증코드를 받을 이메일(@dsm.hs.kr)", example = "example@dsm.hs.kr")
    val email: String,
)
