package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.global.error.SecurityErrorResponder
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
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 조회 API는 principal을 쓰지 않아서 HTTP 테스트로는 누구로 인증했는지 알 수 없다. 필터를 직접 돌려 확인한다.
class ApiKeyAuthenticationFilterTest {

    private val projectRepository = mock(ApiProjectRepository::class.java)
    private val fieldRepository = mock(ApiProjectFieldRepository::class.java)
    private val clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL)
    private val filter = ApiKeyAuthenticationFilter(projectRepository, fieldRepository, clock)

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
    fun `만료 시각이 지나지 않은 키는 인증한다`() {
        stubKey(projectId = 3L, userId = 7L, approved = listOf(ApiKeyField.CLUB), expiresAt = NOW.plusNanos(1_000))
        val request = request("xq_valid")

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNotNull(SecurityContextHolder.getContext().authentication)
        assertNull(request.getAttribute(SecurityErrorResponder.API_KEY_EXPIRED_ATTRIBUTE))
    }

    @Test
    fun `만료 시각이 됐거나 지난 키는 인증하지 않고 만료 표시를 남긴 뒤 다음 필터로 넘긴다`() {
        listOf(NOW, NOW.minusDays(1)).forEach { expiresAt ->
            SecurityContextHolder.clearContext()
            stubKey(projectId = 3L, userId = 7L, approved = listOf(ApiKeyField.CLUB), expiresAt = expiresAt)
            val request = request("xq_valid")
            val chain = MockFilterChain()

            filter.doFilter(request, MockHttpServletResponse(), chain)

            assertNull(SecurityContextHolder.getContext().authentication)
            assertEquals(true, request.getAttribute(SecurityErrorResponder.API_KEY_EXPIRED_ATTRIBUTE))
            assertNotNull(chain.request)
        }
        verifyNoInteractions(fieldRepository)
    }

    @Test
    fun `어느 프로젝트의 키도 아니면 인증하지 않고 다음 필터로 넘긴다`() {
        val chain = MockFilterChain()
        val request = request("xq_unknown")

        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verifyNoInteractions(fieldRepository)
        assertNull(request.getAttribute(SecurityErrorResponder.API_KEY_EXPIRED_ATTRIBUTE))
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

    @Test
    fun `키 헤더가 빈 문자열이거나 공백이면 키가 없는 것과 같아 DB를 조회하지 않는다`() {
        listOf("", " ", "\t ").forEach { blank ->
            val chain = MockFilterChain()

            filter.doFilter(request(blank), MockHttpServletResponse(), chain)

            assertNull(SecurityContextHolder.getContext().authentication)
            assertNotNull(chain.request)
        }
        verifyNoInteractions(projectRepository, fieldRepository)
    }

    // 승인된 분야만 돌려주도록 APPROVED로 물을 때만 응답한다. 다른 상태로 물으면 mock 기본값인 빈 목록이다.
    private fun stubKey(projectId: Long, userId: Long, approved: List<ApiKeyField>, expiresAt: LocalDateTime? = null) {
        val project = ApiProject(userId, "급식 알리미", "급식 공유", NOW)
        ReflectionTestUtils.setField(project, "id", projectId)
        ReflectionTestUtils.setField(project, "expiresAt", expiresAt)
        doReturn(project).`when`(projectRepository).findByKeyHash(ApiKeySecret.hash("xq_valid"))
        doReturn(approved).`when`(fieldRepository).findFieldsByProjectIdAndStatus(projectId, ApplicationStatus.APPROVED)
    }

    private fun request(rawKey: String) = MockHttpServletRequest().apply { addHeader("X-API-Key", rawKey) }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
        private val NOW = LocalDateTime.of(2026, 11, 24, 10, 0)
    }
}
