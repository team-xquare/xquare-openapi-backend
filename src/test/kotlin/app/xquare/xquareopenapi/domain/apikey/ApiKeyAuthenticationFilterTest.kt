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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 조회 API는 principal을 쓰지 않아서 HTTP 테스트로는 누구로 인증했는지 알 수 없다. 필터를 직접 돌려 확인한다.
class ApiKeyAuthenticationFilterTest {

    private val projectRepository = mock(ApiProjectRepository::class.java)
    private val fieldRepository = mock(ApiProjectFieldRepository::class.java)
    private val filter = ApiKeyAuthenticationFilter(projectRepository, fieldRepository)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `프로젝트의 키면 프로젝트 주인의 userId와 승인된 분야 권한으로 인증하고 다음 필터로 넘긴다`() {
        stubKey(projectId = 3L, userId = 7L, approved = listOf(ApiKeyField.STUDENT, ApiKeyField.STUDY_SUPERVISOR))
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
    fun `승인된 분야가 없는 키도 인증은 하고 권한만 비운다`() {
        stubKey(projectId = 3L, userId = 7L, approved = emptyList())

        filter.doFilter(request("xq_valid"), MockHttpServletResponse(), MockFilterChain())

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertTrue(authentication.isAuthenticated)
        assertEquals(emptyList(), authentication.authorities.toList())
    }

    @Test
    fun `어느 프로젝트의 키도 아니면 인증하지 않고 다음 필터로 넘긴다`() {
        val chain = MockFilterChain()

        filter.doFilter(request("xq_unknown"), MockHttpServletResponse(), chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verifyNoInteractions(fieldRepository)
        assertNotNull(chain.request)
    }

    @Test
    fun `키 헤더가 없으면 DB를 조회하지 않고 다음 필터로 넘긴다`() {
        val chain = MockFilterChain()

        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verifyNoInteractions(projectRepository, fieldRepository)
        assertNotNull(chain.request)
    }

    // 승인된 분야만 돌려주도록 APPROVED로 물을 때만 응답한다. 다른 상태로 물으면 mock 기본값인 빈 목록이다.
    private fun stubKey(projectId: Long, userId: Long, approved: List<ApiKeyField>) {
        val project = ApiProject(userId, "급식 알리미", "급식 공유", LocalDateTime.of(2026, 11, 24, 10, 0))
        ReflectionTestUtils.setField(project, "id", projectId)
        doReturn(project).`when`(projectRepository).findByKeyHash(ApiKeySecret.hash("xq_valid"))
        doReturn(approved).`when`(fieldRepository).findFieldsByProjectIdAndStatus(projectId, ApplicationStatus.APPROVED)
    }

    private fun request(rawKey: String) = MockHttpServletRequest().apply { addHeader("X-API-Key", rawKey) }
}
