package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class ApplyProjectFieldsRequest(
    // 같은 분야를 여러 번 넣으면 한 번만 신청한다.
    @field:NotEmpty
    val fields: List<ApiKeyField>,

    // POST /auth/email/otp/verify가 준 토큰. 로그인한 사용자의 이메일로 받은 것이어야 한다.
    @field:NotBlank
    val emailVerifiedToken: String,
)
