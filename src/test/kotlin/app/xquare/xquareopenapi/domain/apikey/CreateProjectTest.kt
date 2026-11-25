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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class CreateProjectTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val jsonMapper: JsonMapper,
    private val jwtProvider: JwtProvider,
) {
    // 이름 검사를 지나친 동시 요청이나 저장 실패를 흉내 낼 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedProjectRepository: ApiProjectRepository

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
        emailVerifiedTokens.failOnDelete = false
    }

    @Test
    fun `교내 분야를 신청하면 바로 승인되고 키를 발급해 원문을 준다`() {
        val before = LocalDateTime.now(SEOUL)
        val result = create(body(fields = """["SCHEDULE","STUDY_SUPERVISOR"]""")).andExpect {
            status { isCreated() }
            jsonPath("$.fields.length()") { value(2) }
            jsonPath("$.fields[0].field") { value("SCHEDULE") }
            jsonPath("$.fields[0].level") { value("SCHOOL") }
            jsonPath("$.fields[0].status") { value("APPROVED") }
            jsonPath("$.fields[1].field") { value("STUDY_SUPERVISOR") }
            jsonPath("$.fields[1].status") { value("APPROVED") }
        }.json()
        val after = LocalDateTime.now(SEOUL)

        val apiKey = result["apiKey"].asString()
        assertTrue(apiKey.startsWith("xq_"))
        val project = projectRepository.findAll().single()
        assertEquals(project.id, result["projectId"].asLong())
        assertEquals(userId, project.userId)
        assertEquals("급식 알리미", project.name)
        assertEquals("반 단톡방에 매일 급식을 올리려고 합니다.", project.purpose)
        assertEquals(ApiKeySecret.hash(apiKey), project.keyHash)
        assertTrue(project.createdAt in before..after, "${project.createdAt}")
        assertEquals(project.createdAt, project.issuedAt)
        val fields = fieldRepository.findAllByProjectIdOrderByIdAsc(project.id!!)
        assertEquals(listOf(ApiKeyField.SCHEDULE, ApiKeyField.STUDY_SUPERVISOR), fields.map { it.field })
        fields.forEach {
            assertEquals(ApplicationStatus.APPROVED, it.status)
            assertEquals(project.createdAt, it.appliedAt)
            // 자동 승인이라 처리 시각은 신청 시각과 같고 처리한 관리자는 없다.
            assertEquals(it.appliedAt, it.decidedAt)
            assertNull(it.decidedBy)
            assertNull(it.rejectReason)
        }
    }

    @Test
    fun `받은 키로 승인된 교내 분야는 바로 조회하고 승인 대기 중인 제한 분야는 조회할 수 없다`() {
        val apiKey = create(body(fields = """["SCHEDULE","STUDENT"]""")).json()["apiKey"].asString()

        mockMvc.get("/schedules?startDate=2026-09-01&finishDate=2026-09-30") { header("X-API-Key", apiKey) }.andExpect { status { isOk() } }
        mockMvc.get("/students") { header("X-API-Key", apiKey) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("API_KEY_FIELD_FORBIDDEN") }
        }
    }

    @Test
    fun `제한 분야만 신청하면 승인 대기이고 키를 발급하지 않는다`() {
        create(body(fields = """["STUDENT","TEACHER"]""")).andExpect {
            status { isCreated() }
            jsonPath("$.apiKey") { value(nullValue()) }
            jsonPath("$.fields[0].level") { value("RESTRICTED") }
            jsonPath("$.fields[0].status") { value("PENDING") }
            jsonPath("$.fields[1].status") { value("PENDING") }
        }

        val project = projectRepository.findAll().single()
        assertNull(project.keyHash)
        assertNull(project.issuedAt)
        assertNull(project.expiresAt)
        fieldRepository.findAll().forEach {
            assertEquals(ApplicationStatus.PENDING, it.status)
            assertNull(it.decidedAt)
        }
    }

    @Test
    fun `교내 분야와 제한 분야를 같이 신청하면 교내만 승인되고 키를 발급한다`() {
        val apiKey = create(body(fields = """["SCHEDULE","STUDENT"]""")).andExpect {
            status { isCreated() }
            jsonPath("$.fields[0].status") { value("APPROVED") }
            jsonPath("$.fields[1].field") { value("STUDENT") }
            jsonPath("$.fields[1].status") { value("PENDING") }
        }.json()["apiKey"].asString()

        assertEquals(ApiKeySecret.hash(apiKey), projectRepository.findAll().single().keyHash)
    }

    @Test
    fun `같은 분야를 여러 번 넣으면 한 번만 신청한다`() {
        create(body(fields = """["CLUB","CLUB","STUDENT","CLUB"]""")).andExpect {
            status { isCreated() }
            jsonPath("$.fields.length()") { value(2) }
        }
        assertEquals(listOf(ApiKeyField.CLUB, ApiKeyField.STUDENT), fieldRepository.findAll().map { it.field })
    }

    @Test
    fun `같은 이름의 내 프로젝트가 있으면 PROJECT_NAME_DUPLICATED이고 토큰은 남긴다`() {
        projectRepository.saveProject(userId, name = "급식 알리미")

        create(body()).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PROJECT_NAME_DUPLICATED") }
            jsonPath("$.message") { value("같은 이름의 프로젝트가 이미 있습니다.") }
        }
        assertEquals(1, projectRepository.count())
        assertEquals(0, fieldRepository.count())
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `이름과 목적은 앞뒤 공백을 다듬어 저장한다`() {
        create(body(name = " 급식 알리미 ", purpose = "  급식 공유  ")).andExpect { status { isCreated() } }

        val project = projectRepository.findAll().single()
        assertEquals("급식 알리미", project.name)
        assertEquals("급식 공유", project.purpose)
    }

    @Test
    fun `앞뒤 공백만 다른 이름은 같은 이름이라 PROJECT_NAME_DUPLICATED`() {
        projectRepository.saveProject(userId, name = "급식 알리미")

        create(body(name = " 급식 알리미 ")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PROJECT_NAME_DUPLICATED") }
        }
        assertEquals(1, projectRepository.count())
    }

    @Test
    fun `다른 사용자의 프로젝트와는 이름이 같아도 된다`() {
        val other = userRepository.saveUser("other")
        projectRepository.saveProject(other.id!!, name = "급식 알리미")

        create(body()).andExpect { status { isCreated() } }
        assertEquals(2, projectRepository.count())
    }

    @Test
    fun `프로젝트는 5개까지 만들고 6번째는 PROJECT_LIMIT_EXCEEDED`() {
        (1..4).forEach { projectRepository.saveProject(userId, name = "프로젝트 $it") }
        // 다른 사용자의 프로젝트는 세지 않는다.
        val other = userRepository.saveUser("other")
        projectRepository.saveProject(other.id!!, name = "남의 프로젝트")

        create(body(name = "다섯 번째")).andExpect { status { isCreated() } }
        emailVerifiedTokens.tokens[OTHER_TOKEN] = EMAIL
        create(body(name = "여섯 번째", token = OTHER_TOKEN)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PROJECT_LIMIT_EXCEEDED") }
            jsonPath("$.message") { value("프로젝트는 최대 5개까지 만들 수 있습니다.") }
        }
        assertEquals(5, projectRepository.countByUserId(userId))
        assertEquals(EMAIL, emailVerifiedTokens.tokens[OTHER_TOKEN])
    }

    @Test
    fun `이름 검사를 지나쳐 이름 제약에 걸려도 PROJECT_NAME_DUPLICATED`() {
        projectRepository.saveProject(userId, name = "급식 알리미")
        doReturn(false).`when`(spiedProjectRepository).existsByUserIdAndName(anyLong(), anyString())

        create(body()).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PROJECT_NAME_DUPLICATED") }
        }
        assertEquals(1, projectRepository.count())
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `이름 제약이 아닌 무결성 위반은 409로 바꾸지 않고 500 INTERNAL_SERVER_ERROR`() {
        doThrow(DataIntegrityViolationException("다른 제약 위반")).`when`(spiedProjectRepository).saveAndFlush(any())

        create(body()).andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_SERVER_ERROR") }
        }
        assertEquals(0, fieldRepository.count())
    }

    @Test
    fun `성공하면 이메일 인증 토큰을 지운다`() {
        create(body()).andExpect { status { isCreated() } }

        assertNull(emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `토큰을 지우지 못해도 프로젝트는 만들어진다`() {
        emailVerifiedTokens.failOnDelete = true

        create(body()).andExpect { status { isCreated() } }
        assertEquals(1, projectRepository.count())
    }

    @Test
    fun `이메일 인증 토큰이 없거나 다른 이메일 것이면 EMAIL_NOT_VERIFIED이고 아무것도 만들지 않는다`() {
        emailVerifiedTokens.tokens[OTHER_TOKEN] = "other@dsm.hs.kr"

        listOf("unknown-token", OTHER_TOKEN).forEach { token ->
            create(body(token = token)).andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
            }
        }
        assertEquals(0, projectRepository.count())
        assertEquals(0, fieldRepository.count())
    }

    @Test
    fun `토큰의 사용자가 없으면 EMAIL_NOT_VERIFIED`() {
        create(body(), jwtProvider.generateAccessToken(userId + 100, Role.USER)).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }
        assertEquals(0, projectRepository.count())
    }

    @Test
    fun `대문자가 섞인 이메일로 가입한 계정도 소문자로 저장된 인증 토큰으로 만들 수 있다`() {
        val mixedCase = userRepository.saveUser("mixed", email = "MixedCase@DSM.hs.kr")
        emailVerifiedTokens.tokens[OTHER_TOKEN] = "mixedcase@dsm.hs.kr"

        create(body(token = OTHER_TOKEN), jwtProvider.generateAccessToken(mixedCase.id!!, Role.USER)).andExpect {
            status { isCreated() }
        }
        assertEquals(mixedCase.id, projectRepository.findAll().single().userId)
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        listOf(
            body(name = " ") to "name",
            body(name = "가".repeat(31)) to "name",
            body(purpose = " ") to "purpose",
            body(purpose = "가".repeat(201)) to "purpose",
            body(token = " ") to "emailVerifiedToken",
            body(fields = "[]") to "fields",
            body(fields = "[null]") to "요청 값이 올바르지 않습니다.",
            body(fields = """["MEAL"]""") to "요청 값이 올바르지 않습니다.",
            """{"name":"급식 알리미","purpose":"급식","emailVerifiedToken":"$TOKEN"}""" to "요청 값이 올바르지 않습니다.",
        ).forEach { (invalid, expectedMessage) ->
            create(invalid).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals(0, projectRepository.count())
    }

    @Test
    fun `모든 분야를 한 번에 신청할 수 있고 이름은 30자, 사용 목적은 200자까지 받는다`() {
        val all = ApiKeyField.entries.joinToString(",", "[", "]") { "\"$it\"" }

        create(body(name = "가".repeat(30), purpose = "가".repeat(200), fields = all)).andExpect { status { isCreated() } }
        assertEquals(ApiKeyField.entries.toSet(), fieldRepository.findAll().map { it.field }.toSet())
    }

    @Test
    fun `액세스 토큰이 없으면 INVALID_TOKEN`() {
        mockMvc.post("/projects") {
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
        assertEquals(0, projectRepository.count())
    }

    private fun create(body: String, token: String = userToken) =
        mockMvc.post("/projects") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun ResultActionsDsl.json() = jsonMapper.readTree(andReturn().response.contentAsString)

    private fun body(
        name: String = "급식 알리미",
        purpose: String = "반 단톡방에 매일 급식을 올리려고 합니다.",
        fields: String = """["SCHEDULE"]""",
        token: String = TOKEN,
    ) = """{"name":"$name","purpose":"$purpose","fields":$fields,"emailVerifiedToken":"$token"}"""

    companion object {
        private const val EMAIL = "junseo@dsm.hs.kr"
        private const val TOKEN = "verified-token"
        private const val OTHER_TOKEN = "other-token"
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
