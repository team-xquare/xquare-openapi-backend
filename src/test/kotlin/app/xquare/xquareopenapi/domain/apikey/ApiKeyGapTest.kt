package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import app.xquare.xquareopenapi.domain.auth.FakeEmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import kotlin.test.assertEquals

// PR #64 pitest에서 살아남은 뮤턴트를 잡는 테스트 모음
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class, MutableClockConfig::class)
class ApiKeyGapTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val clock: MutableClock,
    private val jwtProvider: JwtProvider,
) {
    private val adminToken = jwtProvider.generateAccessToken(ADMIN_ID, Role.ADMIN)
    private var userId = 0L
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        clock.set(NOW)
        userId = userRepository.saveUser("junseo").id!!
        userToken = jwtProvider.generateAccessToken(userId, Role.USER)
    }

    @AfterEach
    fun tearDown() {
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @Test
    fun `키가 발급된 프로젝트 상세는 keyIssued와 발급 시각, 60일 뒤 만료 시각을 보여 준다`() {
        val project = projectRepository.saveProject(userId)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)
        // 승인이 고정된 Clock 시각으로 첫 키를 발급한다.
        mockMvc.post("/admin/api-applications/${application.id}/approve") { bearer(adminToken) }.andExpect { status { isOk() } }

        mockMvc.get("/projects/${project.id}") { bearer(userToken) }.andExpect {
            status { isOk() }
            jsonPath("$.keyIssued") { value(true) }
            jsonPath("$.issuedAt") { value("2026-11-24T09:00:00+09:00") }
            jsonPath("$.expiresAt") { value("2027-01-23T09:00:00+09:00") }
        }
    }

    @Test
    fun `승인하거나 반려한 신청의 관리자 상세는 처리 시각 decidedAt을 보여 준다`() {
        val project = projectRepository.saveProject(userId)
        val approved = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)
        val rejected = fieldRepository.saveField(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING)
        mockMvc.post("/admin/api-applications/${approved.id}/approve") { bearer(adminToken) }.andExpect { status { isOk() } }
        clock.set(NOW.plusHours(1))
        mockMvc.post("/admin/api-applications/${rejected.id}/reject") { bearer(adminToken); reason("사유") }
            .andExpect { status { isOk() } }

        mockMvc.get("/admin/api-applications/${approved.id}") { bearer(adminToken) }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.decidedAt") { value("2026-11-24T09:00:00+09:00") }
        }
        mockMvc.get("/admin/api-applications/${rejected.id}") { bearer(adminToken) }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.decidedAt") { value("2026-11-24T10:00:00+09:00") }
        }
    }

    @Test
    fun `분야가 하나도 없는 프로젝트도 목록에 빈 fields로 나온다`() {
        val project = projectRepository.saveProject(userId, name = "분야 없음")

        mockMvc.get("/projects") { bearer(userToken) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].projectId") { value(project.id!!) }
            jsonPath("$[0].keyIssued") { value(false) }
            jsonPath("$[0].fields") { isArray() }
            jsonPath("$[0].fields.length()") { value(0) }
        }
    }

    @Test
    fun `처리 직전에 프로젝트가 지워져 신청만 남았으면 승인·반려 모두 APPLICATION_NOT_FOUND`() {
        // 신청은 찾히지만 잠글 프로젝트 행이 없는 상태. DecideApiApplicationUseCase의 findByIdForUpdate가 null인 분기를 탄다.
        val orphan = fieldRepository.saveField(DELETED_PROJECT_ID, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        listOf(
            mockMvc.post("/admin/api-applications/${orphan.id}/approve") { bearer(adminToken) },
            mockMvc.post("/admin/api-applications/${orphan.id}/reject") { bearer(adminToken); reason("사유") },
        ).forEach {
            it.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("APPLICATION_NOT_FOUND") }
            }
        }
        assertEquals(ApplicationStatus.PENDING, fieldRepository.findById(orphan.id!!).orElseThrow().status)
    }

    @Test
    fun `사용자 행이 지워진 사용자의 토큰으로 재발급하면 EMAIL_NOT_VERIFIED이고 키는 그대로다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, KEY, ApiKeyField.CLUB)
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        userRepository.deleteAll()

        mockMvc.post("/projects/${project.id}/api-key/reissue") {
            bearer(userToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"emailVerifiedToken":"$TOKEN"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }
        assertEquals(ApiKeySecret.hash(KEY), projectRepository.findById(project.id!!).orElseThrow().keyHash)
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    private fun MockHttpServletRequestDsl.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun MockHttpServletRequestDsl.reason(reason: String) {
        contentType = MediaType.APPLICATION_JSON
        content = """{"reason":"$reason"}"""
    }

    companion object {
        private const val ADMIN_ID = 1L
        private const val DELETED_PROJECT_ID = 999_999L
        private const val KEY = "xq_gap-test-key"
        private const val TOKEN = "verified-token"
        private const val EMAIL = "junseo@dsm.hs.kr"
        private val NOW = LocalDateTime.of(2026, 11, 24, 9, 0)
    }
}
