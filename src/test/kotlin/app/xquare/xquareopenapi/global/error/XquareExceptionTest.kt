package app.xquare.xquareopenapi.global.error

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class XquareExceptionTest {

    @Test
    fun `예외 메시지는 에러 코드의 메시지다`() {
        // 응답은 errorCode로 만들지만 로그에는 예외 메시지가 남는다.
        assertEquals("아이디 또는 비밀번호가 올바르지 않습니다.", XquareException(ErrorCode.INVALID_CREDENTIALS).message)
    }
}
