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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 60일 만료와 갱신·재발급을 Clock 빈 시각을 옮겨 가며 확인한다. Clock 빈을 바꿔서 이 테스트만의 컨텍스트다.
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class, MutableClockConfig::class)
class ApiKeyExpiryTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val clock: MutableClock,
    private val jsonMapper: JsonMapper,
    private val jwtProvider: JwtProvider,
) {
    private var userId = 0L
    private lateinit var userToken: String
    private var tokenCount = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.saveUser("junseo").id!!
        userToken = jwtProvider.generateAccessToken(userId, Role.USER)
        clock.set(ISSUED)
    }

    @AfterEach
    fun tearDown() {
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @Test
    fun `프로젝트를 만들며 받은 키는 발급 시각부터 60일 뒤에 만료된다`() {
        val created = post("/projects", """{"name":"급식 알리미","purpose":"급식","fields":["SCHEDULE"],"emailVerifiedToken":"${token()}"}""")
            .andExpect { status { isCreated() } }.json()
        val apiKey = created["apiKey"].asString()
        val project = projectRepository.findAll().single()
        assertEquals(ISSUED, project.issuedAt)
        assertEquals(ISSUED.plusDays(60), project.expiresAt)

        mockMvc.get("/projects") { header("Authorization", "Bearer $userToken") }.andExpect {
            jsonPath("$[0].issuedAt") { value("2026-11-24T10:00:00+09:00") }
            jsonPath("$[0].expiresAt") { value("2027-01-23T10:00:00+09:00") }
        }
        clock.set(ISSUED.plusDays(60).minusNanos(1_000))
        mockMvc.get("/schedules?startDate=2026-09-01&finishDate=2026-09-30") { header("X-API-Key", apiKey) }.andExpect { status { isOk() } }
        clock.set(ISSUED.plusDays(60))
        mockMvc.get("/schedules?startDate=2026-09-01&finishDate=2026-09-30") { header("X-API-Key", apiKey) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("API_KEY_EXPIRED") }
        }
    }

    @Test
    fun `만료된 키를 갱신하면 키 값은 그대로 두고 지금부터 60일 뒤로 늦춘다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, KEY, ApiKeyField.CLUB)
        clock.set(ISSUED.plusDays(70))
        mockMvc.get("/clubs") { header("X-API-Key", KEY) }.andExpect { jsonPath("$.code") { value("API_KEY_EXPIRED") } }
        val issuedAt = projectRepository.findById(project.id!!).orElseThrow().issuedAt

        renew(project.id!!).andExpect {
            status { isOk() }
            jsonPath("$.expiresAt") { value("2027-04-03T10:00:00+09:00") }
            jsonPath("$.apiKey") { doesNotExist() }
        }

        val renewed = projectRepository.findById(project.id!!).orElseThrow()
        assertEquals(ApiKeySecret.hash(KEY), renewed.keyHash)
        assertEquals(issuedAt, renewed.issuedAt)
        assertEquals(ISSUED.plusDays(130), renewed.expiresAt)
        mockMvc.get("/clubs") { header("X-API-Key", KEY) }.andExpect { status { isOk() } }
    }

    @Test
    fun `재발급한 키도 재발급 시각부터 60일 뒤에 만료된다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, KEY, ApiKeyField.CLUB)
        clock.set(ISSUED.plusDays(10))

        post("/projects/${project.id}/api-key/reissue", """{"emailVerifiedToken":"${token()}"}""").andExpect {
            status { isOk() }
            jsonPath("$.issuedAt") { value("2026-12-04T10:00:00+09:00") }
            jsonPath("$.expiresAt") { value("2027-02-02T10:00:00+09:00") }
        }
        assertEquals(ISSUED.plusDays(70), projectRepository.findById(project.id!!).orElseThrow().expiresAt)
    }

    @Test
    fun `관리자가 첫 승인하면 승인 시각부터 60일 뒤에 만료된다`() {
        val project = projectRepository.saveProject(userId)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)
        clock.set(ISSUED.plusDays(1))

        mockMvc.post("/admin/api-applications/${application.id}/approve") {
            header("Authorization", "Bearer ${jwtProvider.generateAccessToken(1L, Role.ADMIN)}")
        }.andExpect { status { isOk() } }

        assertEquals(ISSUED.plusDays(61), projectRepository.findById(project.id!!).orElseThrow().expiresAt)
    }

    @Test
    fun `키가 아직 없으면 갱신할 수 없어 API_KEY_NOT_ISSUED이고 토큰은 남긴다`() {
        val project = projectRepository.saveProject(userId)
        fieldRepository.saveField(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING)
        val token = token()

        renew(project.id!!, token).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("API_KEY_NOT_ISSUED") }
        }
        assertNull(projectRepository.findById(project.id!!).orElseThrow().expiresAt)
        assertEquals(EMAIL, emailVerifiedTokens.tokens[token])
    }

    @Test
    fun `갱신은 이메일 인증이 필요하고 성공하면 토큰을 지운다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, KEY, ApiKeyField.CLUB)
        val expiresAt = projectRepository.findById(project.id!!).orElseThrow().expiresAt

        renew(project.id!!, "unknown-token").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }
        assertEquals(expiresAt, projectRepository.findById(project.id!!).orElseThrow().expiresAt)

        val token = token()
        clock.set(ISSUED.plusDays(1))
        renew(project.id!!, token).andExpect { status { isOk() } }
        assertNull(emailVerifiedTokens.tokens[token])
    }

    @Test
    fun `없거나 남의 프로젝트는 갱신할 수 없고 형식이 틀리면 INVALID_INPUT`() {
        val other = userRepository.saveUser("other")
        val others = saveApiKey(projectRepository, fieldRepository, other.id!!, KEY, ApiKeyField.CLUB)

        listOf(others.id!!, others.id!! + 100).forEach { projectId ->
            renew(projectId).andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PROJECT_NOT_FOUND") }
            }
        }
        post("/projects/${others.id}/api-key/renew", """{"emailVerifiedToken":" "}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
        mockMvc.post("/projects/${others.id}/api-key/renew") {
            header("X-API-Key", KEY)
            contentType = MediaType.APPLICATION_JSON
            content = """{"emailVerifiedToken":"${token()}"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
    }

    private fun renew(projectId: Long, token: String = token()) =
        post("/projects/$projectId/api-key/renew", """{"emailVerifiedToken":"$token"}""")

    private fun post(path: String, body: String) = mockMvc.post(path) {
        header("Authorization", "Bearer $userToken")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    // 성공하면 토큰이 지워지므로 요청마다 새 토큰을 만든다.
    private fun token(): String {
        val token = "token-${tokenCount++}"
        emailVerifiedTokens.tokens[token] = EMAIL
        return token
    }

    private fun ResultActionsDsl.json() = jsonMapper.readTree(andReturn().response.contentAsString)

    companion object {
        private const val EMAIL = "junseo@dsm.hs.kr"
        private const val KEY = "xq_expiry-key"
        private val ISSUED = LocalDateTime.of(2026, 11, 24, 10, 0)
    }
}
