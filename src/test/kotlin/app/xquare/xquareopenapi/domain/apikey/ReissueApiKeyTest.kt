package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ReissueApiKeyTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val apiKeyRepository: ApiKeyRepository,
    private val jsonMapper: JsonMapper,
    jwtProvider: JwtProvider,
) {
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    @AfterEach
    fun tearDown() {
        apiKeyRepository.deleteAll()
    }

    @Test
    fun `재발급하면 새 키를 주고 기존 키는 바로 폐기한다`() {
        save(2L, "xq_old")

        val response = reissue(userToken).andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val newKey = jsonMapper.readTree(response)["apiKey"].asString()

        assertTrue(newKey.matches(Regex("xq_[A-Za-z0-9_-]{43}")), newKey)
        val saved = apiKeyRepository.findAll().single()
        assertEquals(ApiKeySecret.hash(newKey), saved.keyHash)
        assertNotEquals(ApiKeySecret.hash("xq_old"), saved.keyHash)
        assertEquals("급식 알리미", saved.serviceName)
        assertEquals("급식 공유", saved.purpose)
    }

    @Test
    fun `다른 사용자의 키는 바뀌지 않는다`() {
        save(2L, "xq_mine")
        save(3L, "xq_other")

        reissue(userToken).andExpect { status { isOk() } }

        assertEquals(ApiKeySecret.hash("xq_other"), apiKeyRepository.findByUserId(3L)!!.keyHash)
    }

    @Test
    fun `발급받은 키가 없으면 API_KEY_NOT_FOUND`() {
        reissue(userToken).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("API_KEY_NOT_FOUND") }
            jsonPath("$.message") { value("발급받은 API 키가 없습니다. 활용신청을 먼저 해 주세요.") }
        }
        assertEquals(0, apiKeyRepository.count())
    }

    @Test
    fun `토큰이 없으면 INVALID_TOKEN`() {
        save(2L, "xq_old")

        mockMvc.post("/api-keys/reissue").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
        assertEquals(ApiKeySecret.hash("xq_old"), apiKeyRepository.findAll().single().keyHash)
    }

    private fun save(userId: Long, rawKey: String) =
        apiKeyRepository.save(ApiKey(userId, "급식 알리미", "급식 공유", ApiKeySecret.hash(rawKey)))

    private fun reissue(token: String) =
        mockMvc.post("/api-keys/reissue") {
            header("Authorization", "Bearer $token")
        }
}
