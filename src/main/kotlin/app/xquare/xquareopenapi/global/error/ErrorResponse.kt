package app.xquare.xquareopenapi.global.error

import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["code", "message"])
data class ErrorResponse(
    @field:Schema(description = "에러 코드", example = "INVALID_INPUT")
    val code: String,
    @field:Schema(description = "사람이 읽을 수 있는 에러 메시지", example = "요청 값이 올바르지 않습니다.")
    val message: String,
) {
    companion object {
        fun of(errorCode: ErrorCode, message: String = errorCode.message) =
            ErrorResponse(errorCode.name, message)
    }
}
