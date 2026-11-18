package app.xquare.xquareopenapi.global.error

import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
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

    private fun parameter() = MethodParameter(javaClass.getDeclaredMethod("target", String::class.java), 0)

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun target(request: String) = Unit
}
