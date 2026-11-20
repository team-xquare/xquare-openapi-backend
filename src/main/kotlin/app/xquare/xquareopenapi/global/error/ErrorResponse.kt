package app.xquare.xquareopenapi.global.error

data class ErrorResponse(
    val code: String,
    val message: String,
) {
    companion object {
        fun of(errorCode: ErrorCode, message: String = errorCode.message) =
            ErrorResponse(errorCode.name, message)
    }
}
