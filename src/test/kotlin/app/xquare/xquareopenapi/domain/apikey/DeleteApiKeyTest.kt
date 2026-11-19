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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class DeleteApiKeyTest @Autowired constructor(
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
    fun `삭제하면 키와 모든 신청 기록을 지우고 이메일 인증은 묻지 않는다`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_mine", ApiKeyField.STUDENT, ApiKeyField.TEACHER)

        delete().andExpect {
            status { isOk() }
            jsonPath("$.message") { value("삭제되었습니다.") }
        }

        assertEquals(0, apiKeyRepository.count())
        assertEquals(0, apiKeyApplicationRepository.count())
    }

    @Test
    fun `다른 사용자의 키와 신청 기록은 남긴다`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_mine", ApiKeyField.STUDENT)
        val other = userRepository.saveUser("other")
        val otherKey = saveApiKey(apiKeyRepository, apiKeyApplicationRepository, other.id!!, "xq_other", ApiKeyField.STUDENT, ApiKeyField.CLUB)

        delete().andExpect { status { isOk() } }

        assertEquals(otherKey.id, apiKeyRepository.findAll().single().id)
        assertEquals(listOf(otherKey.id, otherKey.id), apiKeyApplicationRepository.findAll().map { it.apiKeyId })
    }

    @Test
    fun `지운 키로는 바로 조회할 수 없다`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_mine", ApiKeyField.STUDENT)

        delete().andExpect { status { isOk() } }

        mockMvc.get("/students") { header("X-API-Key", "xq_mine") }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_API_KEY") }
        }
    }

    @Test
    fun `지운 뒤 다시 신청하면 첫 신청으로 새 키를 받는다`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_mine", ApiKeyField.STUDENT)
        delete().andExpect { status { isOk() } }
        emailVerifiedTokens.tokens["token"] = "junseo@dsm.hs.kr"

        val response = mockMvc.post("/api-keys") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"field":"STUDENT","serviceName":"급식 알리미","purpose":"급식 공유","emailVerifiedToken":"token"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.type") { value("FIRST") }
        }.andReturn().response.contentAsString

        val newKey = jsonMapper.readTree(response)["apiKey"].asString()
        assertNotEquals("xq_mine", newKey)
        assertEquals(ApiKeyApplicationType.FIRST, apiKeyApplicationRepository.findAll().single().type)
    }

    @Test
    fun `발급받은 키가 없으면 API_KEY_NOT_FOUND`() {
        delete().andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("API_KEY_NOT_FOUND") }
        }
    }

    @Test
    fun `토큰이 없거나 키로 요청하면 INVALID_TOKEN`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_mine", ApiKeyField.STUDENT)

        listOf(
            mockMvc.delete("/api-keys/me"),
            mockMvc.delete("/api-keys/me") { header("X-API-Key", "xq_mine") },
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
        assertEquals(1, apiKeyRepository.count())
    }

    private fun delete() =
        mockMvc.delete("/api-keys/me") { header("Authorization", "Bearer $userToken") }
}
