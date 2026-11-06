package app.xquare.xquareopenapi.global.error

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(XquareException::class)
    fun handleXquareException(e: XquareException): ResponseEntity<ErrorResponse> {
        val response = ResponseEntity.status(e.errorCode.status)
        val retryAfter = e.retryAfter
        if (retryAfter != null) {
            // Retry-After는 초 단위 정수라 남은 시간을 올림한다. 내림하면 그 시각에 다시 요청해도 아직 막힌다.
            response.header(HttpHeaders.RETRY_AFTER, ((retryAfter.toMillis() + 999) / 1000).toString())
        }
        return response.body(ErrorResponse.of(e.errorCode))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return invalidInput(message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        invalidInput(ErrorCode.INVALID_INPUT.message)

    private fun invalidInput(message: String) =
        ResponseEntity.status(ErrorCode.INVALID_INPUT.status).body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message))
}
