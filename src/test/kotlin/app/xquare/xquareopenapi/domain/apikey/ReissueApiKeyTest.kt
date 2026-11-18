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
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class ReissueApiKeyTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
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
        apiKeyApplicationRepository.deleteAll()
        apiKeyRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @Test
    fun `재발급하면 새 키를 주고 기존 키는 바로 폐기하며 신청한 분야는 그대로 둔다`() {
        val saved = save(userId, "xq_old")

        val newKey = reissue().andExpect {
            status { isOk() }
        }.apiKey()

        assertTrue(newKey.matches(Regex("xq_[A-Za-z0-9_-]{43}")), newKey)
        val reissued = apiKeyRepository.findAll().single()
        assertEquals(saved.id, reissued.id)
        assertEquals(ApiKeySecret.hash(newKey), reissued.keyHash)
        assertNotEquals(ApiKeySecret.hash("xq_old"), reissued.keyHash)
        assertEquals(listOf(ApiKeyField.STUDENT, ApiKeyField.TEACHER), apiKeyApplicationRepository.findFieldsByApiKeyId(saved.id!!).sorted())
    }

    @Test
    fun `재발급에 성공하면 이메일 인증 토큰을 지운다`() {
        save(userId, "xq_old")

        reissue().andExpect { status { isOk() } }

        assertNull(emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `다른 사용자의 키는 바뀌지 않는다`() {
        save(userId, "xq_mine")
        val other = userRepository.saveUser("other")
        save(other.id!!, "xq_other")

        reissue().andExpect { status { isOk() } }

        assertEquals(ApiKeySecret.hash("xq_other"), apiKeyRepository.findByUserId(other.id!!)!!.keyHash)
    }

    @Test
    fun `이메일 인증 토큰이 없거나 다른 이메일 것이면 EMAIL_NOT_VERIFIED이고 키는 그대로다`() {
        save(userId, "xq_old")
        emailVerifiedTokens.tokens["other-token"] = "other@dsm.hs.kr"

        listOf("unknown-token", "other-token").forEach { token ->
            reissue(token = token).andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
            }
        }
        assertEquals(ApiKeySecret.hash("xq_old"), apiKeyRepository.findAll().single().keyHash)
    }

    @Test
    fun `이메일 인증 토큰을 빠뜨리면 INVALID_INPUT`() {
        save(userId, "xq_old")

        listOf("""{"emailVerifiedToken":" "}""", "{}").forEach { body ->
            mockMvc.post("/api-keys/reissue") {
                header("Authorization", "Bearer $userToken")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
        assertEquals(ApiKeySecret.hash("xq_old"), apiKeyRepository.findAll().single().keyHash)
    }

    @Test
    fun `발급받은 키가 없으면 API_KEY_NOT_FOUND이고 토큰은 남긴다`() {
        reissue().andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("API_KEY_NOT_FOUND") }
            jsonPath("$.message") { value("발급받은 API 키가 없습니다. 활용신청을 먼저 해 주세요.") }
        }
        assertEquals(0, apiKeyRepository.count())
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `토큰이 없으면 INVALID_TOKEN`() {
        save(userId, "xq_old")

        mockMvc.post("/api-keys/reissue") {
            contentType = MediaType.APPLICATION_JSON
            content = body(TOKEN)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
        assertEquals(ApiKeySecret.hash("xq_old"), apiKeyRepository.findAll().single().keyHash)
    }

    private fun save(userId: Long, rawKey: String) =
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, rawKey, ApiKeyField.STUDENT, ApiKeyField.TEACHER)

    private fun reissue(token: String = TOKEN) =
        mockMvc.post("/api-keys/reissue") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = body(token)
        }

    private fun ResultActionsDsl.apiKey(): String =
        jsonMapper.readTree(andReturn().response.contentAsString)["apiKey"].asString()

    private fun body(token: String) = """{"emailVerifiedToken":"$token"}"""

    companion object {
        private const val EMAIL = "junseo@dsm.hs.kr"
        private const val TOKEN = "verified-token"
    }
}
