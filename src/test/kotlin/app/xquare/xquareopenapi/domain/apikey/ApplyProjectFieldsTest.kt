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
import org.springframework.test.util.ReflectionTestUtils
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
class ApplyProjectFieldsTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val jsonMapper: JsonMapper,
    private val jwtProvider: JwtProvider,
) {
    // 중복 확인을 지나친 동시 신청이나 저장 실패를 흉내 낼 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedFieldRepository: ApiProjectFieldRepository

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
    fun `키가 없는 프로젝트에 교내 분야를 신청하면 바로 승인되고 키를 발급한다`() {
        val project = projectRepository.saveProject(userId)
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        val apiKey = apply(project.id!!, """["SCHEDULE"]""").andExpect {
            status { isCreated() }
            jsonPath("$.projectId") { value(project.id!!) }
            // 응답에는 프로젝트의 모든 분야가 처음 신청한 순서로 온다.
            jsonPath("$.fields.length()") { value(2) }
            jsonPath("$.fields[0].field") { value("STUDENT") }
            jsonPath("$.fields[0].status") { value("PENDING") }
            jsonPath("$.fields[1].field") { value("SCHEDULE") }
            jsonPath("$.fields[1].level") { value("SCHOOL") }
            jsonPath("$.fields[1].status") { value("APPROVED") }
        }.json()["apiKey"].asString()

        val saved = projectRepository.findById(project.id!!).orElseThrow()
        assertEquals(ApiKeySecret.hash(apiKey), saved.keyHash)
        assertEquals(fieldRepository.findAll().single { it.field == ApiKeyField.SCHEDULE }.appliedAt, saved.issuedAt)
    }

    @Test
    fun `이미 키가 있으면 분야만 더하고 키는 다시 주지 않는다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, RAW_KEY, ApiKeyField.CLUB)

        apply(project.id!!, """["SCHEDULE"]""").andExpect {
            status { isCreated() }
            jsonPath("$.apiKey") { value(nullValue()) }
            jsonPath("$.fields[1].status") { value("APPROVED") }
        }
        assertEquals(ApiKeySecret.hash(RAW_KEY), projectRepository.findById(project.id!!).orElseThrow().keyHash)
        assertEquals(2, fieldRepository.count())
    }

    @Test
    fun `제한 분야만 신청해도 첫 신청이면 키를 바로 발급하고, 승인 전에는 그 분야를 조회할 수 없다`() {
        val project = projectRepository.saveProject(userId)
        val before = LocalDateTime.now(SEOUL)

        val apiKey = apply(project.id!!, """["TEACHER"]""").andExpect {
            status { isCreated() }
            jsonPath("$.fields[0].level") { value("RESTRICTED") }
            jsonPath("$.fields[0].status") { value("PENDING") }
        }.json()["apiKey"].asString()

        assertTrue(apiKey.startsWith("xq_"))
        val saved = projectRepository.findById(project.id!!).orElseThrow()
        assertEquals(ApiKeySecret.hash(apiKey), saved.keyHash)
        assertTrue(saved.issuedAt!! >= before, "${saved.issuedAt}")
        assertEquals(saved.issuedAt!!.plusDays(ApiProject.KEY_LIFETIME_DAYS), saved.expiresAt)
        val pending = fieldRepository.findAll().single()
        assertNull(pending.decidedAt)
        mockMvc.get("/teachers") { header("X-API-Key", apiKey) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("API_KEY_FIELD_FORBIDDEN") }
        }
    }

    @Test
    fun `급식(MEAL)은 공개 단계라 신청하면 바로 승인한다`() {
        val project = projectRepository.saveProject(userId)

        apply(project.id!!, """["MEAL"]""").andExpect {
            status { isCreated() }
            jsonPath("$.fields[0].field") { value("MEAL") }
            jsonPath("$.fields[0].level") { value("PUBLIC") }
            jsonPath("$.fields[0].status") { value("APPROVED") }
        }
        val meal = fieldRepository.findAll().single()
        assertEquals(ApplicationStatus.APPROVED, meal.status)
        assertEquals(meal.appliedAt, meal.decidedAt)
        assertNull(meal.decidedBy)
    }

    @Test
    fun `분야마다 사용 목적을 앞뒤 공백을 다듬어 저장하고 응답에도 준다`() {
        val project = projectRepository.saveProject(userId)

        applyRaw(project.id!!, """[{"field":"SCHEDULE","purpose":"  일정 알림  "},{"field":"STUDENT","purpose":"학생 인증"}]""").andExpect {
            status { isCreated() }
            jsonPath("$.fields[0].purpose") { value("일정 알림") }
            jsonPath("$.fields[1].purpose") { value("학생 인증") }
        }
        val saved = fieldRepository.findAllByProjectIdOrderByIdAsc(project.id!!)
        assertEquals(listOf("일정 알림", "학생 인증"), saved.map { it.purpose })
    }

    @Test
    fun `프로젝트의 첫 신청 요청에 들어 있던 분야는 FIRST, 그 뒤 요청의 분야는 ADDITIONAL이다`() {
        val project = projectRepository.saveProject(userId)

        apply(project.id!!, """["SCHEDULE","STUDENT"]""").andExpect { status { isCreated() } }
        emailVerifiedTokens.tokens[TOKEN] = EMAIL
        apply(project.id!!, """["CLUB"]""").andExpect { status { isCreated() } }

        val types = fieldRepository.findAllByProjectIdOrderByIdAsc(project.id!!).associate { it.field to it.applicationType }
        assertEquals(
            mapOf(
                ApiKeyField.SCHEDULE to ApplicationType.FIRST,
                ApiKeyField.STUDENT to ApplicationType.FIRST,
                ApiKeyField.CLUB to ApplicationType.ADDITIONAL,
            ),
            types,
        )
    }

    @Test
    fun `같은 분야를 두 번 넣으면 어느 목적인지 알 수 없어 INVALID_INPUT이고 아무것도 남기지 않는다`() {
        val project = projectRepository.saveProject(userId)

        apply(project.id!!, """["CLUB","STUDENT","CLUB"]""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
        assertEquals(0, fieldRepository.count())
        assertNull(projectRepository.findById(project.id!!).orElseThrow().keyHash)
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `모든 분야를 한 번에 신청할 수 있고 사용 목적은 200자까지 받는다`() {
        val project = projectRepository.saveProject(userId)
        val all = ApiKeyField.entries.joinToString(",", "[", "]") { """{"field":"$it","purpose":"${"가".repeat(200)}"}""" }

        applyRaw(project.id!!, all).andExpect { status { isCreated() } }
        assertEquals(ApiKeyField.entries.toSet(), fieldRepository.findAll().map { it.field }.toSet())
    }

    @Test
    fun `대문자가 섞인 이메일로 가입한 계정도 소문자로 저장된 인증 토큰으로 신청할 수 있다`() {
        val mixedCase = userRepository.saveUser("mixed", email = "MixedCase@DSM.hs.kr")
        val project = projectRepository.saveProject(mixedCase.id!!)
        emailVerifiedTokens.tokens["mixed-token"] = "mixedcase@dsm.hs.kr"

        mockMvc.post("/projects/${project.id}/fields") {
            header("Authorization", "Bearer ${jwtProvider.generateAccessToken(mixedCase.id!!, Role.USER)}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fields":${fieldsJson("CLUB")},"emailVerifiedToken":"mixed-token"}"""
        }.andExpect { status { isCreated() } }
        assertEquals(1, fieldRepository.count())
    }

    @Test
    fun `토큰을 지우지 못해도 신청은 끝난다`() {
        val project = projectRepository.saveProject(userId)
        emailVerifiedTokens.failOnDelete = true

        apply(project.id!!, """["CLUB"]""").andExpect { status { isCreated() } }
        assertEquals(1, fieldRepository.count())
    }

    @Test
    fun `대기 중이거나 승인된 분야를 다시 신청하면 FIELD_ALREADY_APPLIED이고 토큰은 남긴다`() {
        val project = projectRepository.saveProject(userId)
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)
        fieldRepository.saveField(project.id!!, ApiKeyField.CLUB, ApplicationStatus.APPROVED)

        listOf("""["STUDENT"]""", """["TEACHER","CLUB"]""").forEach { fields ->
            apply(project.id!!, fields).andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("FIELD_ALREADY_APPLIED") }
                jsonPath("$.message") { value("이미 신청했거나 승인된 분야입니다.") }
            }
        }
        // 함께 보낸 TEACHER도 남기지 않는다.
        assertEquals(2, fieldRepository.count())
        assertNull(projectRepository.findById(project.id!!).orElseThrow().keyHash)
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `반려된 분야는 다시 신청하면 승인 대기로 돌아가고 반려 기록을 지우며 목적은 새로 받고 구분은 그대로다`() {
        val project = projectRepository.saveProject(userId)
        val rejected = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.REJECTED)
        ReflectionTestUtils.setField(rejected, "rejectReason", "목적이 불분명합니다.")
        ReflectionTestUtils.setField(rejected, "decidedAt", LocalDateTime.of(2026, 11, 1, 9, 0))
        ReflectionTestUtils.setField(rejected, "decidedBy", 1L)
        fieldRepository.save(rejected)
        val before = LocalDateTime.now(SEOUL)

        apply(project.id!!, """["STUDENT"]""").andExpect {
            status { isCreated() }
            jsonPath("$.fields[0].status") { value("PENDING") }
        }

        val resubmitted = fieldRepository.findAll().single()
        assertEquals(rejected.id, resubmitted.id)
        assertEquals(ApplicationStatus.PENDING, resubmitted.status)
        assertNull(resubmitted.rejectReason)
        assertNull(resubmitted.decidedAt)
        assertNull(resubmitted.decidedBy)
        assertTrue(resubmitted.appliedAt >= before, "${resubmitted.appliedAt}")
        assertEquals(PURPOSE, resubmitted.purpose)
        assertEquals(ApplicationType.FIRST, resubmitted.applicationType)
    }

    @Test
    fun `없거나 남의 프로젝트면 PROJECT_NOT_FOUND`() {
        val other = userRepository.saveUser("other")
        val othersProject = projectRepository.saveProject(other.id!!)

        listOf(othersProject.id!!, othersProject.id!! + 100).forEach { projectId ->
            apply(projectId, """["CLUB"]""").andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PROJECT_NOT_FOUND") }
                jsonPath("$.message") { value("존재하지 않는 프로젝트입니다.") }
            }
        }
        assertEquals(0, fieldRepository.count())
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `이메일 인증이 필요하다`() {
        val project = projectRepository.saveProject(userId)

        apply(project.id!!, """["CLUB"]""", token = "unknown-token").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }
        assertEquals(0, fieldRepository.count())
    }

    @Test
    fun `성공하면 이메일 인증 토큰을 지운다`() {
        val project = projectRepository.saveProject(userId)

        apply(project.id!!, """["CLUB"]""").andExpect { status { isCreated() } }
        assertNull(emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `중복 확인이 앞 요청의 분야를 못 보고 분야 제약에 걸려도 FIELD_ALREADY_APPLIED`() {
        val project = projectRepository.saveProject(userId)
        fieldRepository.saveField(project.id!!, ApiKeyField.CLUB)
        doReturn(emptyList<ApiProjectField>()).`when`(spiedFieldRepository).findAllByProjectIdOrderByIdAsc(anyLong())

        apply(project.id!!, """["CLUB"]""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("FIELD_ALREADY_APPLIED") }
        }
        assertEquals(1, fieldRepository.count())
        assertEquals(EMAIL, emailVerifiedTokens.tokens[TOKEN])
    }

    @Test
    fun `분야 제약이 아닌 무결성 위반은 409로 바꾸지 않고 500 INTERNAL_SERVER_ERROR`() {
        val project = projectRepository.saveProject(userId)
        doThrow(DataIntegrityViolationException("다른 제약 위반")).`when`(spiedFieldRepository).save(any<ApiProjectField>())

        apply(project.id!!, """["CLUB"]""").andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_SERVER_ERROR") }
        }
        assertEquals(0, fieldRepository.count())
        assertNull(projectRepository.findById(project.id!!).orElseThrow().keyHash)
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        val project = projectRepository.saveProject(userId)

        listOf(
            """{"fields":[],"emailVerifiedToken":"$TOKEN"}""" to "fields",
            """{"fields":[null],"emailVerifiedToken":"$TOKEN"}""" to "요청 값이 올바르지 않습니다.",
            // 예전 형식(분야 이름만)은 사용 목적이 없어 받지 않는다.
            """{"fields":["CLUB"],"emailVerifiedToken":"$TOKEN"}""" to "요청 값이 올바르지 않습니다.",
            """{"fields":[{"field":"TIMETABLE","purpose":"p"}],"emailVerifiedToken":"$TOKEN"}""" to "요청 값이 올바르지 않습니다.",
            """{"fields":[{"purpose":"p"}],"emailVerifiedToken":"$TOKEN"}""" to "요청 값이 올바르지 않습니다.",
            """{"fields":[{"field":"CLUB","purpose":" "}],"emailVerifiedToken":"$TOKEN"}""" to "purpose",
            """{"fields":[{"field":"CLUB","purpose":"${"가".repeat(201)}"}],"emailVerifiedToken":"$TOKEN"}""" to "purpose",
            """{"fields":[{"field":"CLUB"}],"emailVerifiedToken":"$TOKEN"}""" to "요청 값이 올바르지 않습니다.",
            """{"fields":${fieldsJson("CLUB")},"emailVerifiedToken":" "}""" to "emailVerifiedToken",
            """{"emailVerifiedToken":"$TOKEN"}""" to "요청 값이 올바르지 않습니다.",
        ).forEach { (invalid, expectedMessage) ->
            mockMvc.post("/projects/${project.id}/fields") {
                header("Authorization", "Bearer $userToken")
                contentType = MediaType.APPLICATION_JSON
                content = invalid
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals(0, fieldRepository.count())
    }

    @Test
    fun `액세스 토큰이 없으면 INVALID_TOKEN`() {
        val project = projectRepository.saveProject(userId)

        mockMvc.post("/projects/${project.id}/fields") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"fields":${fieldsJson("CLUB")},"emailVerifiedToken":"$TOKEN"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
    }

    // fields는 ["CLUB","STUDENT"]처럼 분야 이름만 적고, 사용 목적은 모두 PURPOSE로 채운다.
    private fun apply(projectId: Long, fields: String, token: String = TOKEN) =
        applyRaw(projectId, fieldsJson(*Regex("\\w+").findAll(fields).map { it.value }.toList().toTypedArray()), token)

    private fun applyRaw(projectId: Long, fieldsJson: String, token: String = TOKEN) =
        mockMvc.post("/projects/$projectId/fields") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fields":$fieldsJson,"emailVerifiedToken":"$token"}"""
        }

    private fun fieldsJson(vararg fields: String) =
        fields.joinToString(",", "[", "]") { """{"field":"$it","purpose":"$PURPOSE"}""" }

    private fun ResultActionsDsl.json() = jsonMapper.readTree(andReturn().response.contentAsString)

    companion object {
        private const val EMAIL = "junseo@dsm.hs.kr"
        private const val TOKEN = "verified-token"
        private const val RAW_KEY = "xq_existing"
        private const val PURPOSE = "반 단톡방에 알림을 보내려고 합니다."
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
