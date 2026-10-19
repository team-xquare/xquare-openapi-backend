package app.xquare.xquareopenapi.global.error

import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(XquareException::class)
    fun handleXquareException(e: XquareException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.errorCode.status).body(ErrorResponse.of(e.errorCode))

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
