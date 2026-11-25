package app.xquare.xquareopenapi.domain.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["email", "otp"])
data class EmailOtpVerifyRequest(
    @field:Schema(description = "인증코드를 발송받은 이메일(@dsm.hs.kr)", example = "example@dsm.hs.kr")
    val email: String,
    @field:jakarta.validation.constraints.NotBlank
    @field:Schema(description = "이메일로 발송된 6자리 인증코드", example = "483920")
    val otp: String,
)
