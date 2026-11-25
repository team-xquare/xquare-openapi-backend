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
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class ReissueProjectApiKeyTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val jsonMapper: JsonMapper,
    private val jwtProvider: JwtProvider,
) {
    private var userId = 0L
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        userId = userRepository.saveUser("junseo").id!!
        userToken = jwtProvider.generateAccessToken(userId, Role.USER)
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
    }

    @AfterEach
    fun tearDown() {
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @Test
    fun `재발급하면 새 키 원문을 주고 이전 키는 바로 못 쓰며 분야는 그대로다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, OLD_KEY, ApiKeyField.CLUB)
        val before = LocalDateTime.now(SEOUL)

        val response = reissue(project.id!!).andExpect { status { isOk() } }.json()

        val newKey = response["apiKey"].asString()
        assertTrue(newKey.startsWith("xq_"))
        assertNotEquals(OLD_KEY, newKey)
        val saved = projectRepository.findById(project.id!!).orElseThrow()
        assertEquals(ApiKeySecret.hash(newKey), saved.keyHash)
        assertTrue(saved.issuedAt!! >= before, "${saved.issuedAt}")
        // DB는 마이크로초까지 반올림해 저장하므로 응답 시각과는 1마이크로초 안에서 같다.
        val responded = OffsetDateTime.parse(response["issuedAt"].asString()).atZoneSameInstant(SEOUL).toLocalDateTime()
        assertTrue(Duration.between(saved.issuedAt, responded).abs() < Duration.of(1, ChronoUnit.MICROS), "$responded")
        assertNull(emailVerifiedTokens.tokens[TOKEN])
        mockMvc.get("/clubs") { header("X-API-Key", OLD_KEY) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_API_KEY") }
        }
        mockMvc.get("/clubs") { header("X-API-Key", newKey) }.andExpect { status { isOk() } }
    }

    @Test
    fun `관리자 승인으로 처음 발급돼 원문을 받지 못한 키도 재발급으로 받는다`() {
        // 관리자 승인은 해시만 저장한다. 원문은 아무도 모른다.
        val project = projectRepository.saveProject(userId, rawKey = "xq_unknown-to-anyone")
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT)

        val newKey = reissue(project.id!!).andExpect { status { isOk() } }.json()["apiKey"].asString()

        mockMvc.get("/students") { header("X-API-Key", newKey) }.andExpect { status { isOk() } }
    }

    @Test
    fun `아직 키가 발급되지 않은 프로젝트면 API_KEY_NOT_ISSUED이고 토큰은 남긴다`() {
        val project = projectRepository.saveProject(userId)
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        reissue(project.id!!).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("API_KEY_NOT_ISSUED") }
            jsonPath("$.message") { value("아직 발급된 API 키가 없습니다. 분야가 승인된 뒤 다시 시도해 주세요.") }
        }
        assertNull(projectRepository.findById(project.id!!).orElseThrow().keyHash)
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `없거나 남의 프로젝트면 PROJECT_NOT_FOUND`() {
        val other = userRepository.saveUser("other")
        val others = saveApiKey(projectRepository, fieldRepository, other.id!!, OLD_KEY, ApiKeyField.CLUB)

        listOf(others.id!!, others.id!! + 100).forEach { projectId ->
            reissue(projectId).andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PROJECT_NOT_FOUND") }
            }
        }
        assertEquals(ApiKeySecret.hash(OLD_KEY), projectRepository.findById(others.id!!).orElseThrow().keyHash)
    }

    @Test
    fun `이메일 인증이 필요하다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, OLD_KEY, ApiKeyField.CLUB)

        reissue(project.id!!, token = "unknown-token").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }
        assertEquals(ApiKeySecret.hash(OLD_KEY), projectRepository.findById(project.id!!).orElseThrow().keyHash)
    }

    @Test
    fun `이메일 인증 토큰이 비었으면 INVALID_INPUT`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, OLD_KEY, ApiKeyField.CLUB)

        listOf("""{"emailVerifiedToken":" "}""", "{}").forEach { body ->
            mockMvc.post("/projects/${project.id}/api-key/reissue") {
                header("Authorization", "Bearer $userToken")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
    }

    @Test
    fun `API 키로는 재발급할 수 없다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, OLD_KEY, ApiKeyField.CLUB)

        mockMvc.post("/projects/${project.id}/api-key/reissue") {
            header("X-API-Key", OLD_KEY)
            contentType = MediaType.APPLICATION_JSON
            content = """{"emailVerifiedToken":"$TOKEN"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
    }

    private fun reissue(projectId: Long, token: String = TOKEN) =
        mockMvc.post("/projects/$projectId/api-key/reissue") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"emailVerifiedToken":"$token"}"""
        }

    private fun ResultActionsDsl.json() = jsonMapper.readTree(andReturn().response.contentAsString)

    companion object {
        private const val EMAIL = "junseo@dsm.hs.kr"
        private const val TOKEN = "verified-token"
        private const val OLD_KEY = "xq_old-key"
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
