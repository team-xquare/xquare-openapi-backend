package app.xquare.xquareopenapi.global.error

import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(XquareException::class)
    fun handleXquareException(e: XquareException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.errorCode.status).body(ErrorResponse.of(e.errorCode))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        // 쿼리 파라미터 타입 변환 실패(예: grade=abc)의 기본 메시지는 내부 타입 이름을 드러내서 바꾼다.
        val message = e.bindingResult.fieldErrors.joinToString(", ") {
            "${it.field}: ${if (it.isBindingFailure) TYPE_MISMATCH_MESSAGE else it.defaultMessage}"
        }
        return invalidInput(message)
    }

    // 경로 변수가 타입에 맞지 않을 때 난다(예: /students/abc).
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        invalidInput(ErrorCode.INVALID_INPUT.message)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        invalidInput(ErrorCode.INVALID_INPUT.message)

    // 데드락이나 잠금 대기 시간 초과로 DB가 트랜잭션을 되돌린 경우다(CannotAcquireLockException 등).
    // 다시 보내면 처리되는 실패라 형식 없는 500 대신 409로 알려 준다.
    @ExceptionHandler(PessimisticLockingFailureException::class)
    fun handleLockFailure(e: PessimisticLockingFailureException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.CONCURRENT_UPDATE.status).body(ErrorResponse.of(ErrorCode.CONCURRENT_UPDATE))

    private fun invalidInput(message: String) =
        ResponseEntity.status(ErrorCode.INVALID_INPUT.status).body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message))

    companion object {
        private const val TYPE_MISMATCH_MESSAGE = "형식이 올바르지 않습니다."
    }
}
