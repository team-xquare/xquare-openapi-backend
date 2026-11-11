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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// 조회 API는 principal을 쓰지 않아서 HTTP 테스트로는 누구로 인증했는지 알 수 없다. 필터를 직접 돌려 확인한다.
class ApiKeyAuthenticationFilterTest {

    private val apiKeyRepository = mock(ApiKeyRepository::class.java)
    private val filter = ApiKeyAuthenticationFilter(apiKeyRepository)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `발급된 키면 키 주인의 userId와 API_KEY 권한으로 인증하고 다음 필터로 넘긴다`() {
        doReturn(ApiKey(7L, "급식 알리미", "급식 공유", ApiKeySecret.hash("xq_valid")))
            .`when`(apiKeyRepository).findByKeyHash(ApiKeySecret.hash("xq_valid"))
        val chain = MockFilterChain()

        filter.doFilter(request("xq_valid"), MockHttpServletResponse(), chain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertEquals(7L, authentication.principal)
        assertEquals(listOf("API_KEY"), authentication.authorities.map { it.authority })
        assertNotNull(chain.request)
    }

    @Test
    fun `발급되지 않은 키면 인증하지 않고 다음 필터로 넘긴다`() {
        val chain = MockFilterChain()

        filter.doFilter(request("xq_unknown"), MockHttpServletResponse(), chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request)
    }

    @Test
    fun `키 헤더가 없으면 DB를 조회하지 않고 다음 필터로 넘긴다`() {
        val chain = MockFilterChain()

        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verifyNoInteractions(apiKeyRepository)
        assertNotNull(chain.request)
    }

    private fun request(rawKey: String) = MockHttpServletRequest().apply { addHeader("X-API-Key", rawKey) }
}
