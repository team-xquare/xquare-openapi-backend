package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import app.xquare.xquareopenapi.domain.auth.FakeEmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class ApplyApiKeyTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
    private val userRepository: UserRepository,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val jsonMapper: JsonMapper,
    private val jwtProvider: JwtProvider,
) {
    // 동시 신청을 흉내 내거나 저장 실패를 일으킬 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedApiKeyRepository: ApiKeyRepository

    // MySQL REPEATABLE READ에서 중복 확인이 옛 스냅샷을 보는 상황을 흉내 낼 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedApplicationRepository: ApiKeyApplicationRepository

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
        emailVerifiedTokens.failOnDelete = false
    }

    @Test
    fun `첫 신청이면 키를 발급하고 신청 기록을 FIRST로 남긴다`() {
        val before = LocalDateTime.now(SEOUL)
        val apiKey = apply(body(field = "STUDENT")).andExpect {
            status { isCreated() }
            jsonPath("$.type") { value("FIRST") }
        }.apiKey()
        val after = LocalDateTime.now(SEOUL)

        assertTrue(apiKey.startsWith("xq_"))
        val savedKey = apiKeyRepository.findAll().single()
        assertEquals(userId, savedKey.userId)
        assertEquals(ApiKeySecret.hash(apiKey), savedKey.keyHash)
        val application = apiKeyApplicationRepository.findAll().single()
        assertEquals(savedKey.id, application.apiKeyId)
        assertEquals(ApiKeyField.STUDENT, application.field)
        assertEquals(ApiKeyApplicationType.FIRST, application.type)
        assertEquals("급식 알리미", application.serviceName)
        assertEquals("반 단톡방에 매일 급식을 올리려고 합니다.", application.purpose)
        // 신청 시각은 서버 시간대와 상관없이 한국 시간이다.
        assertTrue(application.appliedAt in before..after, "${application.appliedAt}")
    }

    @Test
    fun `키가 있으면 같은 키에 분야만 더하고 키는 다시 주지 않는다`() {
        val apiKey = apply(body(field = "STUDENT")).apiKey()
        emailVerifiedTokens.tokens[OTHER_TOKEN] = EMAIL

        apply(body(field = "TEACHER", serviceName = "선생님 찾기", purpose = "교무실 위치 안내", token = OTHER_TOKEN)).andExpect {
            status { isCreated() }
            jsonPath("$.type") { value("ADD_FIELD") }
            jsonPath("$.apiKey") { value(nullValue()) }
        }

        val savedKey = apiKeyRepository.findAll().single()
        assertEquals(ApiKeySecret.hash(apiKey), savedKey.keyHash)
        val added = apiKeyApplicationRepository.findAll().single { it.field == ApiKeyField.TEACHER }
        assertEquals(savedKey.id, added.apiKeyId)
        assertEquals(ApiKeyApplicationType.ADD_FIELD, added.type)
        assertEquals("선생님 찾기", added.serviceName)
        assertEquals("교무실 위치 안내", added.purpose)
        assertEquals(2, apiKeyApplicationRepository.count())
    }

    @Test
    fun `이미 신청한 분야면 API_KEY_FIELD_ALREADY_APPLIED이고 토큰은 남긴다`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_existing", ApiKeyField.STUDENT)

        apply(body(field = "STUDENT")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("API_KEY_FIELD_ALREADY_APPLIED") }
            jsonPath("$.message") { value("이미 신청한 분야입니다.") }
        }
        assertEquals(1, apiKeyApplicationRepository.count())
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `다른 사용자가 신청한 분야는 내 신청과 상관없다`() {
        val other = userRepository.saveUser("other")
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, other.id!!, "xq_other", ApiKeyField.STUDENT)

        val apiKey = apply(body(field = "STUDENT")).andExpect {
            status { isCreated() }
            jsonPath("$.type") { value("FIRST") }
        }.apiKey()

        assertNotEquals(ApiKeySecret.hash("xq_other"), ApiKeySecret.hash(apiKey))
        assertEquals(2, apiKeyRepository.count())
        assertEquals(ApiKeySecret.hash("xq_other"), apiKeyRepository.findByUserId(other.id!!)!!.keyHash)
    }

    @Test
    fun `신청에 성공하면 이메일 인증 토큰을 지운다`() {
        apply(body()).andExpect { status { isCreated() } }

        assertEquals(null, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `토큰을 지우지 못해도 신청은 성공한다`() {
        emailVerifiedTokens.failOnDelete = true

        apply(body()).andExpect { status { isCreated() } }
        assertEquals(1, apiKeyRepository.count())
    }

    @Test
    fun `이메일 인증 토큰이 없거나 다른 이메일 것이면 EMAIL_NOT_VERIFIED이고 아무것도 만들지 않는다`() {
        emailVerifiedTokens.tokens[OTHER_TOKEN] = "other@dsm.hs.kr"

        listOf("unknown-token", OTHER_TOKEN).forEach { token ->
            apply(body(token = token)).andExpectEmailNotVerified()
        }
        assertEquals(0, apiKeyRepository.count())
        assertEquals(0, apiKeyApplicationRepository.count())
        assertEquals("other@dsm.hs.kr", emailVerifiedTokens.tokens[OTHER_TOKEN])
    }

    @Test
    fun `대문자가 섞인 이메일로 가입한 계정도 소문자로 저장된 인증 토큰으로 신청할 수 있다`() {
        val mixedCase = userRepository.saveUser("mixed", email = "MixedCase@DSM.hs.kr")
        emailVerifiedTokens.tokens[OTHER_TOKEN] = "mixedcase@dsm.hs.kr"

        apply(body(token = OTHER_TOKEN), jwtProvider.generateAccessToken(mixedCase.id!!, Role.USER)).andExpect {
            status { isCreated() }
        }
        assertEquals(mixedCase.id, apiKeyRepository.findAll().single().userId)
    }

    @Test
    fun `정규화 전에 대문자 그대로 저장된 인증 토큰도 같은 이메일이면 받는다`() {
        emailVerifiedTokens.tokens[OTHER_TOKEN] = " JunSeo@dsm.hs.kr "

        apply(body(token = OTHER_TOKEN)).andExpect { status { isCreated() } }
    }

    @Test
    fun `분야를 더할 때도 이메일 인증이 필요하다`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_existing", ApiKeyField.STUDENT)

        apply(body(field = "TEACHER", token = "unknown-token")).andExpectEmailNotVerified()
        assertEquals(1, apiKeyApplicationRepository.count())
    }

    @Test
    fun `토큰의 사용자가 없으면 EMAIL_NOT_VERIFIED`() {
        val token = jwtProvider.generateAccessToken(userId + 100, Role.USER)

        apply(body(), token).andExpectEmailNotVerified()
        assertEquals(0, apiKeyRepository.count())
    }

    @Test
    fun `중복 확인이 앞 요청의 분야를 못 보고 분야 제약에 걸려도 API_KEY_FIELD_ALREADY_APPLIED`() {
        val apiKey = saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_existing", ApiKeyField.STUDENT)
        doReturn(false).`when`(spiedApplicationRepository).existsByApiKeyIdAndField(apiKey.id!!, ApiKeyField.STUDENT)

        apply(body(field = "STUDENT")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("API_KEY_FIELD_ALREADY_APPLIED") }
        }
        assertEquals(1, apiKeyApplicationRepository.count())
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `분야를 더할 때 분야 제약이 아닌 무결성 위반은 409로 바꾸지 않고 500 INTERNAL_SERVER_ERROR`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_existing", ApiKeyField.STUDENT)
        doThrow(DataIntegrityViolationException("다른 제약 위반")).`when`(spiedApplicationRepository).saveAndFlush(any())

        apply(body(field = "TEACHER")).andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_SERVER_ERROR") }
        }
        assertEquals(1, apiKeyApplicationRepository.count())
    }

    @Test
    fun `동시 첫 신청으로 먼저 키가 저장됐으면 API_KEY_ALREADY_EXISTS`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_existing", ApiKeyField.STUDENT)
        doReturn(null).`when`(spiedApiKeyRepository).findByUserId(anyLong())

        apply(body(field = "TEACHER")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("API_KEY_ALREADY_EXISTS") }
        }
        assertEquals(1, apiKeyRepository.count())
        assertEquals(1, apiKeyApplicationRepository.count())
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `키를 찾은 뒤 잠그기 전에 키가 지워졌으면 첫 신청으로 새 키를 발급한다`() {
        val deleted = saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_deleted")
        apiKeyRepository.deleteAll()
        doReturn(deleted).`when`(spiedApiKeyRepository).findByUserId(userId)

        val apiKey = apply(body()).andExpect {
            jsonPath("$.type") { value("FIRST") }
        }.apiKey()
        assertEquals(ApiKeySecret.hash(apiKey), apiKeyRepository.findAll().single().keyHash)
    }

    @Test
    fun `사용자 제약이 아닌 무결성 위반은 409로 바꾸지 않고 500 INTERNAL_SERVER_ERROR`() {
        doThrow(DataIntegrityViolationException("다른 제약 위반")).`when`(spiedApiKeyRepository).saveAndFlush(any())

        apply(body()).andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_SERVER_ERROR") }
        }
        assertEquals(0, apiKeyApplicationRepository.count())
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        listOf(
            body(serviceName = " ") to "serviceName",
            body(serviceName = "가".repeat(31)) to "serviceName",
            body(purpose = " ") to "purpose",
            body(purpose = "가".repeat(201)) to "purpose",
            body(token = " ") to "emailVerifiedToken",
            body(field = "MEAL") to "요청 값이 올바르지 않습니다.",
            """{"serviceName":"급식 알리미","purpose":"급식","emailVerifiedToken":"$TOKEN"}""" to "요청 값이 올바르지 않습니다.",
        ).forEach { (invalid, expectedMessage) ->
            apply(invalid).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals(0, apiKeyRepository.count())
    }

    @Test
    fun `모든 분야를 신청할 수 있고 서비스 이름은 30자, 사용 목적은 200자까지 받는다`() {
        ApiKeyField.entries.forEachIndexed { index, field ->
            emailVerifiedTokens.tokens["token-$index"] = EMAIL
            apply(body(field = field.name, serviceName = "가".repeat(30), purpose = "가".repeat(200), token = "token-$index"))
                .andExpect { status { isCreated() } }
        }
        assertEquals(ApiKeyField.entries.toSet(), apiKeyApplicationRepository.findAll().map { it.field }.toSet())
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

    private fun apply(body: String, token: String = userToken) =
        mockMvc.post("/api-keys") {
            header("Authorization", "Bearer $token")
            json(body)
        }

    private fun ResultActionsDsl.apiKey(): String =
        jsonMapper.readTree(andReturn().response.contentAsString)["apiKey"].asString()

    private fun ResultActionsDsl.andExpectEmailNotVerified() = andExpect {
        status { isForbidden() }
        jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
    }

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun body(
        field: String = "STUDENT",
        serviceName: String = "급식 알리미",
        purpose: String = "반 단톡방에 매일 급식을 올리려고 합니다.",
        token: String = TOKEN,
    ) = """{"field":"$field","serviceName":"$serviceName","purpose":"$purpose","emailVerifiedToken":"$token"}"""

    companion object {
        private const val EMAIL = "junseo@dsm.hs.kr"
        private const val TOKEN = "verified-token"
        private const val OTHER_TOKEN = "other-token"
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
