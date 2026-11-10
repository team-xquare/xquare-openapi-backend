package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import jakarta.servlet.ServletException
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class IssueApiKeyTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val apiKeyRepository: ApiKeyRepository,
    private val jsonMapper: JsonMapper,
    private val jwtProvider: JwtProvider,
) {
    // 동시 신청을 흉내 내려고 중복 검사만 통과시킬 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedApiKeyRepository: ApiKeyRepository

    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    @AfterEach
    fun tearDown() {
        apiKeyRepository.deleteAll()
    }

    @Test
    fun `신청하면 키를 발급하고 원문은 응답으로만 주고 DB에는 해시만 저장한다`() {
        val apiKey = issue(userToken).andExpect {
            status { isCreated() }
        }.apiKey()

        assertTrue(apiKey.startsWith("xq_"))
        val saved = apiKeyRepository.findAll().single()
        assertEquals(2L, saved.userId)
        assertEquals("급식 알리미", saved.serviceName)
        assertEquals("반 단톡방에 매일 급식을 올리려고 합니다.", saved.purpose)
        assertEquals(ApiKeySecret.hash(apiKey), saved.keyHash)
    }

    @Test
    fun `사용자마다 다른 키를 발급한다`() {
        val first = issue(userToken).apiKey()
        val second = issue(jwtProvider.generateAccessToken(3L, Role.ADMIN)).apiKey()

        assertNotEquals(first, second)
        assertEquals(setOf(2L, 3L), apiKeyRepository.findAll().map { it.userId }.toSet())
        assertEquals(2, apiKeyRepository.findAll().map { it.id }.toSet().size)
    }

    @Test
    fun `이미 키가 있으면 API_KEY_ALREADY_EXISTS`() {
        saveKeyOf(2L)

        issue(userToken).andExpectAlreadyExists()
        assertEquals(1, apiKeyRepository.count())
        // 중복 검사에서 막아야 운영 DB의 제약 이름이 달라도 409가 난다. 제약에 맡기면 이름을 구분하지 못해 500이 된다.
        verify(spiedApiKeyRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `동시 신청으로 중복 검사를 통과해도 같은 사용자는 API_KEY_ALREADY_EXISTS`() {
        saveKeyOf(2L)
        doReturn(false).`when`(spiedApiKeyRepository).existsByUserId(anyLong())

        issue(userToken).andExpectAlreadyExists()
        assertEquals(1, apiKeyRepository.count())
    }

    @Test
    fun `사용자 제약이 아닌 무결성 위반은 원래 예외를 그대로 던진다`() {
        val violation = DataIntegrityViolationException("다른 제약 위반")
        doThrow(violation).`when`(spiedApiKeyRepository).saveAndFlush(any())

        val thrown = assertFailsWith<ServletException> { issue(userToken) }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        listOf(
            body(serviceName = " ") to "serviceName",
            body(serviceName = "가".repeat(31)) to "serviceName",
            body(purpose = " ") to "purpose",
            body(purpose = "가".repeat(201)) to "purpose",
            """{"serviceName":"급식 알리미"}""" to "요청 값이 올바르지 않습니다.",
        ).forEach { (invalid, expectedMessage) ->
            issue(userToken, invalid).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals(0, apiKeyRepository.count())
    }

    @Test
    fun `서비스 이름은 30자, 사용 목적은 200자까지 받는다`() {
        issue(userToken, body(serviceName = "가".repeat(30), purpose = "가".repeat(200))).andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `토큰이 없으면 INVALID_TOKEN`() {
        mockMvc.post("/api-keys") {
            json(body())
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
        assertEquals(0, apiKeyRepository.count())
    }

    private fun saveKeyOf(userId: Long) =
        apiKeyRepository.save(ApiKey(userId, "기존 서비스", "기존 목적", ApiKeySecret.hash("xq_existing")))

    private fun issue(token: String, body: String = body()) =
        mockMvc.post("/api-keys") {
            header("Authorization", "Bearer $token")
            json(body)
        }

    private fun ResultActionsDsl.apiKey(): String =
        jsonMapper.readTree(andReturn().response.contentAsString)["apiKey"].asString()

    private fun ResultActionsDsl.andExpectAlreadyExists() = andExpect {
        status { isConflict() }
        jsonPath("$.code") { value("API_KEY_ALREADY_EXISTS") }
        jsonPath("$.message") { value("이미 발급받은 API 키가 있습니다. 재발급을 이용해 주세요.") }
    }

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun body(
        serviceName: String = "급식 알리미",
        purpose: String = "반 단톡방에 매일 급식을 올리려고 합니다.",
    ) = """{"serviceName":"$serviceName","purpose":"$purpose"}"""
}
