package app.xquare.xquareopenapi.domain.auth.presentation.dto

import jakarta.validation.constraints.NotBlank

// 형식 규칙은 검사하지 않는다. 형식이 틀린 값도 일치하는 계정이 없으므로 INVALID_CREDENTIALS가 된다.
data class LoginRequest(
    @field:NotBlank
    val username: String,

    @field:NotBlank
    val password: String,
)
