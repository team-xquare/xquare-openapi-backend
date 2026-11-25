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

    @Test
    fun `조회 API에 키가 없거나 틀리면 401 INVALID_API_KEY로 응답한다`() {
        val response = MockHttpServletResponse()

        responder.invalidApiKey.commence(MockHttpServletRequest(), response, InsufficientAuthenticationException("no key"))

        assertEquals(401, response.status)
        assertEquals("""{"code":"INVALID_API_KEY","message":"API 키가 없거나 유효하지 않습니다."}""", response.contentAsString)
    }

    @Test
    fun `필터가 만료 표시를 남긴 요청이면 401 API_KEY_EXPIRED로 응답한다`() {
        val request = MockHttpServletRequest().apply { setAttribute(SecurityErrorResponder.API_KEY_EXPIRED_ATTRIBUTE, true) }
        val response = MockHttpServletResponse()

        responder.invalidApiKey.commence(request, response, InsufficientAuthenticationException("expired"))

        assertEquals(401, response.status)
        assertEquals("""{"code":"API_KEY_EXPIRED","message":"만료된 API 키입니다. 키를 갱신해 주세요."}""", response.contentAsString)
    }

    @Test
    fun `승인되지 않은 분야면 403 API_KEY_FIELD_FORBIDDEN으로 응답한다`() {
        val response = MockHttpServletResponse()

        responder.apiKeyFieldForbidden.handle(MockHttpServletRequest(), response, AccessDeniedException("denied"))

        assertEquals(403, response.status)
        assertEquals("API_KEY_FIELD_FORBIDDEN", JsonMapper.builder().build().readTree(response.contentAsString)["code"].asString())
    }
}
