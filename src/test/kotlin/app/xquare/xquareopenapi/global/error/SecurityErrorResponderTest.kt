package app.xquare.xquareopenapi.global.error

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.InsufficientAuthenticationException
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

class SecurityErrorResponderTest {

    private val responder = SecurityErrorResponder(JsonMapper.builder().build())

    @Test
    fun `인증이 없으면 401 INVALID_TOKEN으로 응답한다`() {
        val response = MockHttpServletResponse()

        responder.commence(MockHttpServletRequest(), response, InsufficientAuthenticationException("no token"))

        assertEquals(401, response.status)
        assertEquals("application/json;charset=UTF-8", response.contentType)
        assertEquals("""{"code":"INVALID_TOKEN","message":"토큰이 없거나 유효하지 않습니다."}""", response.contentAsString)
    }

    @Test
    fun `권한이 부족하면 403 FORBIDDEN으로 응답한다`() {
        val response = MockHttpServletResponse()

        responder.handle(MockHttpServletRequest(), response, AccessDeniedException("denied"))

        assertEquals(403, response.status)
        assertEquals("application/json;charset=UTF-8", response.contentType)
        assertEquals("""{"code":"FORBIDDEN","message":"권한이 없습니다."}""", response.contentAsString)
    }
}
