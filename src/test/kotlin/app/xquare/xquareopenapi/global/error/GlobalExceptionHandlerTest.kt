package app.xquare.xquareopenapi.global.error

import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `검증 실패는 필드별 기본 메시지를, 타입 변환 실패는 내부 타입을 숨긴 메시지를 준다`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "request").apply {
            addError(FieldError("request", "size", 101, false, null, null, "100 이하여야 합니다"))
            addError(FieldError("request", "grade", "abc", true, null, null, "Failed to convert 'java.lang.String'"))
        }

        val response = handler.handleValidationException(MethodArgumentNotValidException(parameter(), bindingResult))

        assertEquals(400, response.statusCode.value())
        assertEquals(
            ErrorResponse("INVALID_INPUT", "size: 100 이하여야 합니다, grade: 형식이 올바르지 않습니다."),
            response.body,
        )
    }

    @Test
    fun `데드락이나 잠금 대기 시간 초과로 트랜잭션이 되돌려지면 409 CONCURRENT_UPDATE`() {
        val expected = ErrorResponse("CONCURRENT_UPDATE", "동시에 수정되어 처리하지 못했습니다. 다시 시도해 주세요.")
        for (e in listOf(CannotAcquireLockException("Deadlock detected"), PessimisticLockingFailureException("Lock wait timeout"))) {
            val response = handler.handleLockFailure(e)

            assertEquals(409, response.statusCode.value())
            assertEquals(expected, response.body)
        }
    }

    private fun parameter() = MethodParameter(javaClass.getDeclaredMethod("target", String::class.java), 0)

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun target(request: String) = Unit
}
