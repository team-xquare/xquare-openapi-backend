package app.xquare.xquareopenapi.domain.auth.presentation.dto

data class EmailOtpVerifyRequest(
    val email: String,
    @field:jakarta.validation.constraints.NotBlank
    val otp: String,
)
