package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ApiKeyApplicationRequest(
    val field: ApiKeyField,

    @field:NotBlank
    @field:Size(max = 30)
    val serviceName: String,

    @field:NotBlank
    @field:Size(max = 200)
    val purpose: String,

    // POST /auth/email/otp/verify가 준 토큰. 로그인한 사용자의 이메일로 받은 것이어야 한다.
    @field:NotBlank
    val emailVerifiedToken: String,
)
