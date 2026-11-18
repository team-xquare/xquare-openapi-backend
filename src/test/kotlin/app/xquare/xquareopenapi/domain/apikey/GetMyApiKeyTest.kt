package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import app.xquare.xquareopenapi.domain.auth.FakeEmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class GetMyApiKeyTest @Autowired constructor(
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
    }

    @AfterEach
    fun tearDown() {
        apiKeyApplicationRepository.deleteAll()
        apiKeyRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @Test
    fun `발급 시각, 만료 없음(null), 신청한 분야를 신청 순서대로 보여 주고 키 원문은 주지 않는다`() {
        val before = nowInSeoul()
        apply("TEACHER")
        apply("STUDENT")
        val after = nowInSeoul()
        val apiKey = apiKeyRepository.findAll().single()
        val applications = apiKeyApplicationRepository.findAll().sortedBy { it.id }

        val body = getMine().andExpect {
            status { isOk() }
            jsonPath("$.expiresAt") { value(nullValue()) }
            jsonPath("$.fields.length()") { value(2) }
            jsonPath("$.fields[0].field") { value("TEACHER") }
            jsonPath("$.fields[1].field") { value("STUDENT") }
        }.andReturn().response.contentAsString

        val json = jsonMapper.readTree(body)
        assertEquals(apiKey.issuedAt, LocalDateTime.parse(json["issuedAt"].asString()))
        assertTrue(apiKey.issuedAt in before..after, "${apiKey.issuedAt}")
        assertEquals(applications.map { it.appliedAt }, (0 until json["fields"].size()).map { LocalDateTime.parse(json["fields"][it]["appliedAt"].asString()) })
        assertEquals(setOf("issuedAt", "expiresAt", "fields"), json.properties().map { it.key }.toSet())
        assertFalse(body.contains("xq_"))
    }

    @Test
    fun `재발급하면 발급 시각이 바뀐다`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_old", ApiKeyField.STUDENT)
        // 메모리의 값은 나노초까지 있고 DB는 마이크로초로 반올림해 저장한다. 같은 기준으로 비교하려고 DB에서 다시 읽는다.
        val issuedAt = apiKeyRepository.findAll().single().issuedAt
        emailVerifiedTokens.tokens["reissue"] = EMAIL

        mockMvc.post("/api-keys/reissue") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"emailVerifiedToken":"reissue"}"""
        }.andExpect { status { isOk() } }

        val reissuedAt = apiKeyRepository.findAll().single().issuedAt
        assertTrue(reissuedAt > issuedAt, "$issuedAt -> $reissuedAt")
        val body = getMine().andReturn().response.contentAsString
        assertEquals(reissuedAt, LocalDateTime.parse(jsonMapper.readTree(body)["issuedAt"].asString()))
    }

    @Test
    fun `다른 사용자의 분야는 보이지 않는다`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_mine", ApiKeyField.CLUB)
        val other = userRepository.saveUser("other")
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, other.id!!, "xq_other", ApiKeyField.STUDENT, ApiKeyField.TEACHER)

        getMine().andExpect {
            jsonPath("$.fields.length()") { value(1) }
            jsonPath("$.fields[0].field") { value("CLUB") }
        }
    }

    @Test
    fun `발급받은 키가 없으면 API_KEY_NOT_FOUND`() {
        getMine().andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("API_KEY_NOT_FOUND") }
        }
    }

    @Test
    fun `토큰이 없거나 키로 요청하면 INVALID_TOKEN`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_mine", ApiKeyField.STUDENT)

        listOf(
            mockMvc.get("/api-keys/me"),
            mockMvc.get("/api-keys/me") { header("X-API-Key", "xq_mine") },
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
    }

    private fun apply(field: String) {
        val token = "token-$field"
        emailVerifiedTokens.tokens[token] = EMAIL
        mockMvc.post("/api-keys") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"field":"$field","serviceName":"급식 알리미","purpose":"급식 공유","emailVerifiedToken":"$token"}"""
        }.andExpect { status { isCreated() } }
    }

    private fun getMine() = mockMvc.get("/api-keys/me") { header("Authorization", "Bearer $userToken") }

    companion object {
        private const val EMAIL = "junseo@dsm.hs.kr"
    }
}
