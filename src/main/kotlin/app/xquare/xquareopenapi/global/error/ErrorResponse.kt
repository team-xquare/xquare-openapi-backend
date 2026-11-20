package app.xquare.xquareopenapi.global.error

import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["code", "message"])
data class ErrorResponse(
    val code: String,
    val message: String,
) {
    companion object {
        fun of(errorCode: ErrorCode, message: String = errorCode.message) =
            ErrorResponse(errorCode.name, message)
    }
}
