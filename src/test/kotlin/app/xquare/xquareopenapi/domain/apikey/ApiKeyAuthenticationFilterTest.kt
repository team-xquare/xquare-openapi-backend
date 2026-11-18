package app.xquare.xquareopenapi.domain.apikey

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 조회 API는 principal을 쓰지 않아서 HTTP 테스트로는 누구로 인증했는지 알 수 없다. 필터를 직접 돌려 확인한다.
class ApiKeyAuthenticationFilterTest {

    private val apiKeyRepository = mock(ApiKeyRepository::class.java)
    private val apiKeyApplicationRepository = mock(ApiKeyApplicationRepository::class.java)
    private val filter = ApiKeyAuthenticationFilter(apiKeyRepository, apiKeyApplicationRepository)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `발급된 키면 키 주인의 userId와 신청한 분야 권한으로 인증하고 다음 필터로 넘긴다`() {
        stubKey(id = 3L, userId = 7L, fields = listOf(ApiKeyField.STUDENT, ApiKeyField.STUDY_SUPERVISOR))
        val chain = MockFilterChain()

        filter.doFilter(request("xq_valid"), MockHttpServletResponse(), chain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertTrue(authentication.isAuthenticated)
        assertEquals(7L, authentication.principal)
        assertEquals(
            listOf("API_KEY_FIELD_STUDENT", "API_KEY_FIELD_STUDY_SUPERVISOR"),
            authentication.authorities.map { it.authority },
        )
        assertNotNull(chain.request)
    }

    @Test
    fun `신청한 분야가 없는 키도 인증은 하고 권한만 비운다`() {
        stubKey(id = 3L, userId = 7L, fields = emptyList())

        filter.doFilter(request("xq_valid"), MockHttpServletResponse(), MockFilterChain())

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertTrue(authentication.isAuthenticated)
        assertEquals(emptyList(), authentication.authorities.toList())
    }

    @Test
    fun `발급되지 않은 키면 인증하지 않고 다음 필터로 넘긴다`() {
        val chain = MockFilterChain()

        filter.doFilter(request("xq_unknown"), MockHttpServletResponse(), chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verifyNoInteractions(apiKeyApplicationRepository)
        assertNotNull(chain.request)
    }

    @Test
    fun `키 헤더가 없으면 DB를 조회하지 않고 다음 필터로 넘긴다`() {
        val chain = MockFilterChain()

        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verifyNoInteractions(apiKeyRepository, apiKeyApplicationRepository)
        assertNotNull(chain.request)
    }

    private fun stubKey(id: Long, userId: Long, fields: List<ApiKeyField>) {
        val apiKey = ApiKey(userId, ApiKeySecret.hash("xq_valid"))
        ReflectionTestUtils.setField(apiKey, "id", id)
        doReturn(apiKey).`when`(apiKeyRepository).findByKeyHash(ApiKeySecret.hash("xq_valid"))
        doReturn(fields).`when`(apiKeyApplicationRepository).findFieldsByApiKeyId(id)
    }

    private fun request(rawKey: String) = MockHttpServletRequest().apply { addHeader("X-API-Key", rawKey) }
}
