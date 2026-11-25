package app.xquare.xquareopenapi.domain.auth.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

// 형식 규칙은 검사하지 않는다. 형식이 틀린 값도 일치하는 계정이 없으므로 INVALID_CREDENTIALS가 된다.
@Schema(requiredProperties = ["username", "password"])
data class LoginRequest(
    @field:NotBlank
    @field:Schema(description = "로그인 아이디", example = "Jun227")
    val username: String,

    @field:NotBlank
    @field:Schema(description = "비밀번호", example = "Password1!")
    val password: String,
)
