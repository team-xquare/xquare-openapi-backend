package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class ApplyProjectFieldsRequest(
    // 분야마다 사용 목적을 받는다. 같은 분야를 두 번 넣으면 어느 목적인지 알 수 없어 400이다.
    @field:NotEmpty
    @field:Valid
    val fields: List<FieldApplication>,

    // POST /auth/email/otp/verify가 준 토큰. 로그인한 사용자의 이메일로 받은 것이어야 한다.
    @field:NotBlank
    val emailVerifiedToken: String,
) {
    data class FieldApplication(
        val field: ApiKeyField,

        @field:NotBlank
        @field:Size(max = 200)
        val purpose: String,
    )
}
