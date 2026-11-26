package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiApplicationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
    jwtProvider: JwtProvider,
) {
    private val adminToken = jwtProvider.generateAccessToken(ADMIN_ID, Role.ADMIN)
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)
    private var userId = 0L

    @BeforeEach
    fun setUp() {
        userId = userRepository.saveUser("junseo").id!!
    }

    @AfterEach
    fun tearDown() {
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
        userRepository.deleteAll()
    }

    // 목록

    @Test
    fun `목록은 최근 신청부터 프로젝트 이름, 신청자, 분야, 단계, 상태, 구분을 보여 준다`() {
        val project = projectRepository.saveProject(userId, name = "급식 알리미")
        val first = field(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING, LocalDateTime.of(2026, 11, 24, 9, 0))
        val second = field(project.id!!, ApiKeyField.CLUB, ApplicationStatus.APPROVED, LocalDateTime.of(2026, 11, 24, 10, 0))

        mockMvc.get("/admin/api-applications") { bearer(adminToken) }.andExpect {
            status { isOk() }
            jsonPath("$.totalCount") { value(2) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(20) }
            jsonPath("$.content[*].applicationId") { value(contains(second.id!!.toInt(), first.id!!.toInt())) }
            jsonPath("$.content[1].projectId") { value(project.id!!) }
            jsonPath("$.content[1].projectName") { value("급식 알리미") }
            jsonPath("$.content[1].userId") { value(userId) }
            jsonPath("$.content[1].applicantName") { value("김준서") }
            jsonPath("$.content[1].applicationType") { value("FIRST") }
            jsonPath("$.content[1].field") { value("STUDENT") }
            jsonPath("$.content[1].level") { value("RESTRICTED") }
            jsonPath("$.content[1].status") { value("PENDING") }
            jsonPath("$.content[1].appliedAt") { value("2026-11-24T09:00:00+09:00") }
            jsonPath("$.content[1].decidedAt") { value(nullValue()) }
        }
    }

    @Test
    fun `조건에 맞는 신청이 없으면 빈 목록이다`() {
        mockMvc.get("/admin/api-applications?status=PENDING") { bearer(adminToken) }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(0) }
            jsonPath("$.totalCount") { value(0) }
            jsonPath("$.totalPage") { value(0) }
        }
    }

    @Test
    fun `상태로 거르고 페이지를 나눈다`() {
        val project = projectRepository.saveProject(userId)
        val now = LocalDateTime.of(2026, 11, 24, 9, 0)
        val pending = listOf(ApiKeyField.STUDENT, ApiKeyField.TEACHER).mapIndexed { i, f ->
            field(project.id!!, f, ApplicationStatus.PENDING, now.plusMinutes(i.toLong()))
        }
        field(project.id!!, ApiKeyField.CLUB, ApplicationStatus.APPROVED, now.plusMinutes(5))
        field(project.id!!, ApiKeyField.SCHEDULE, ApplicationStatus.REJECTED, now.plusMinutes(6))

        mockMvc.get("/admin/api-applications?status=PENDING&page=1&size=1") { bearer(adminToken) }.andExpect {
            status { isOk() }
            jsonPath("$.totalCount") { value(2) }
            jsonPath("$.totalPage") { value(2) }
            jsonPath("$.content.length()") { value(1) }
            jsonPath("$.content[0].applicationId") { value(pending[0].id!!) }
        }
        mockMvc.get("/admin/api-applications?status=REJECTED") { bearer(adminToken) }.andExpect {
            jsonPath("$.content[*].field") { value(contains("SCHEDULE")) }
        }
        mockMvc.get("/admin/api-applications") { bearer(adminToken) }.andExpect {
            jsonPath("$.totalCount") { value(4) }
        }
    }

    @Test
    fun `신청 시각이 같으면 id 역순이다`() {
        val project = projectRepository.saveProject(userId)
        val now = LocalDateTime.of(2026, 11, 24, 9, 0)
        val a = field(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING, now)
        val b = field(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING, now)

        mockMvc.get("/admin/api-applications") { bearer(adminToken) }.andExpect {
            jsonPath("$.content[*].applicationId") { value(contains(b.id!!.toInt(), a.id!!.toInt())) }
        }
    }

    @Test
    fun `분야, 단계, 구분으로 거르고 함께 주면 모두 맞는 신청만 보여 준다`() {
        val project = projectRepository.saveProject(userId)
        val now = LocalDateTime.of(2026, 11, 24, 9, 0)
        field(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING, now)
        field(project.id!!, ApiKeyField.SCHEDULE, ApplicationStatus.APPROVED, now.plusMinutes(1), ApplicationType.ADDITIONAL)
        field(project.id!!, ApiKeyField.MEAL, ApplicationStatus.APPROVED, now.plusMinutes(2), ApplicationType.ADDITIONAL)
        field(project.id!!, ApiKeyField.CLUB, ApplicationStatus.PENDING, now.plusMinutes(3), ApplicationType.ADDITIONAL)

        fun fields(query: String) = mockMvc.get("/admin/api-applications?$query") { bearer(adminToken) }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
            .let { Regex("\"field\":\"(\\w+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }

        assertEquals(listOf("SCHEDULE"), fields("field=SCHEDULE"))
        assertEquals(listOf("CLUB", "STUDENT"), fields("level=RESTRICTED"))
        assertEquals(listOf("MEAL"), fields("level=PUBLIC"))
        assertEquals(listOf("STUDENT"), fields("type=FIRST"))
        assertEquals(listOf("CLUB", "MEAL", "SCHEDULE"), fields("type=ADDITIONAL"))
        assertEquals(listOf("CLUB"), fields("type=ADDITIONAL&level=RESTRICTED&status=PENDING"))
        // 분야와 단계가 서로 맞지 않으면 고를 분야가 없어 빈 목록이다.
        assertEquals(emptyList(), fields("field=STUDENT&level=SCHOOL"))
        mockMvc.get("/admin/api-applications?field=STUDENT&level=SCHOOL&page=2&size=5") { bearer(adminToken) }.andExpect {
            jsonPath("$.totalCount") { value(0) }
            jsonPath("$.totalPage") { value(0) }
            jsonPath("$.page") { value(2) }
            jsonPath("$.size") { value(5) }
        }
    }

    @Test
    fun `키워드는 프로젝트 이름이나 신청자 이름의 일부로 찾고 특수 문자는 글자 그대로 비교한다`() {
        val other = userRepository.save(User(username = "minji", password = "encoded", studentNumber = 2101, name = "이민지", email = "minji@dsm.hs.kr"))
        val mine = projectRepository.saveProject(userId, name = "급식_알리미")
        val hers = projectRepository.saveProject(other.id!!, name = "시간표 봇 100%")
        val now = LocalDateTime.of(2026, 11, 24, 9, 0)
        field(mine.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING, now)
        field(hers.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING, now.plusMinutes(1))

        fun names(keyword: String) = mockMvc.get("/admin/api-applications") {
            bearer(adminToken)
            param("keyword", keyword)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
            .let { Regex("\"projectName\":\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }

        assertEquals(listOf("급식_알리미"), names("급식"))
        assertEquals(listOf("시간표 봇 100%"), names("민지"))
        assertEquals(listOf("급식_알리미"), names("식_알"))
        assertEquals(emptyList(), names("식x알"))
        assertEquals(listOf("시간표 봇 100%"), names("100%"))
        assertEquals(emptyList(), names("1%0"))
        // 앞뒤 공백은 다듬어서 찾는다.
        assertEquals(listOf("급식_알리미"), names(" 급식 "))
    }

    @Test
    fun `신청자 행이 지워진 신청도 목록에 나오고 신청자 이름은 null이다`() {
        val project = projectRepository.saveProject(userId + 100)
        field(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING, LocalDateTime.of(2026, 11, 24, 9, 0))

        mockMvc.get("/admin/api-applications") { bearer(adminToken) }.andExpect {
            jsonPath("$.totalCount") { value(1) }
            jsonPath("$.content[0].applicantName") { value(nullValue()) }
        }
        mockMvc.get("/admin/api-applications?keyword=테스트") { bearer(adminToken) }.andExpect {
            jsonPath("$.totalCount") { value(1) }
        }
    }

    @Test
    fun `목록 조건이 범위를 벗어나거나 없는 값이면 INVALID_INPUT`() {
        listOf(
            "page=-1", "size=0", "size=101", "page=21474837", "status=DONE",
            "field=TIMETABLE", "level=OPEN", "type=SECOND", "keyword=${"가".repeat(31)}",
        ).forEach { query ->
            mockMvc.get("/admin/api-applications?$query") { bearer(adminToken) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
        mockMvc.get("/admin/api-applications?page=21474836&size=100") { bearer(adminToken) }.andExpect { status { isOk() } }
        // 공백뿐인 키워드는 거를 값이 없어 400이다. URL 템플릿은 %를 다시 인코딩하므로 param으로 보낸다.
        mockMvc.get("/admin/api-applications") {
            bearer(adminToken)
            param("keyword", "  ")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    // 상세

    @Test
    fun `상세는 신청 내용, 처리 기록, 프로젝트와 그 분야 전체, 신청자를 보여 준다`() {
        val project = projectRepository.saveProject(userId, name = "급식 알리미")
        fieldRepository.saveField(project.id!!, ApiKeyField.CLUB)
        val application = field(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING, LocalDateTime.of(2026, 11, 24, 9, 0))

        mockMvc.get("/admin/api-applications/${application.id}") { bearer(adminToken) }.andExpect {
            status { isOk() }
            jsonPath("$.applicationId") { value(application.id!!) }
            jsonPath("$.field") { value("STUDENT") }
            jsonPath("$.level") { value("RESTRICTED") }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.rejectReason") { value(nullValue()) }
            jsonPath("$.appliedAt") { value("2026-11-24T09:00:00+09:00") }
            jsonPath("$.decidedAt") { value(nullValue()) }
            jsonPath("$.decidedBy") { value(nullValue()) }
            jsonPath("$.project.projectId") { value(project.id!!) }
            jsonPath("$.project.name") { value("급식 알리미") }
            jsonPath("$.purpose") { value("테스트 목적") }
            jsonPath("$.applicationType") { value("FIRST") }
            jsonPath("$.project.purpose") { doesNotExist() }
            jsonPath("$.project.keyIssued") { value(false) }
            jsonPath("$.project.serviceUrl") { value(nullValue()) }
            jsonPath("$.project.fields[*].field") { value(contains("CLUB", "STUDENT")) }
            jsonPath("$.userId") { value(userId) }
            jsonPath("$.applicant.username") { value("junseo") }
            jsonPath("$.applicant.name") { value("김준서") }
            jsonPath("$.applicant.email") { value("junseo@dsm.hs.kr") }
            jsonPath("$.applicant.studentNumber") { value(2315) }
        }
    }

    @Test
    fun `신청자 행이 지워졌으면 applicant는 null이다`() {
        val project = projectRepository.saveProject(userId + 100)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        mockMvc.get("/admin/api-applications/${application.id}") { bearer(adminToken) }.andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(userId + 100) }
            jsonPath("$.applicant") { value(nullValue()) }
        }
    }

    @Test
    fun `없는 신청이면 APPLICATION_NOT_FOUND이고 숫자가 아니면 INVALID_INPUT`() {
        listOf(
            mockMvc.get("/admin/api-applications/999999") { bearer(adminToken) },
            mockMvc.post("/admin/api-applications/999999/approve") { bearer(adminToken) },
            mockMvc.post("/admin/api-applications/999999/reject") { bearer(adminToken); reason("사유") },
        ).forEach {
            it.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("APPLICATION_NOT_FOUND") }
                jsonPath("$.message") { value("존재하지 않는 신청입니다.") }
            }
        }
        mockMvc.get("/admin/api-applications/abc") { bearer(adminToken) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    // 승인

    @Test
    fun `승인하면 처리 기록을 남기고 첫 승인이면 키 해시만 발급하며 원문은 주지 않는다`() {
        val project = projectRepository.saveProject(userId)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)
        val before = LocalDateTime.now(SEOUL)

        mockMvc.post("/admin/api-applications/${application.id}/approve") { bearer(adminToken) }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.decidedBy") { value(ADMIN_ID) }
            jsonPath("$.project.keyIssued") { value(true) }
            jsonPath("$.apiKey") { doesNotExist() }
            content { string(not(containsString("xq_"))) }
        }

        val approved = fieldRepository.findById(application.id!!).orElseThrow()
        assertEquals(ApplicationStatus.APPROVED, approved.status)
        assertEquals(ADMIN_ID, approved.decidedBy)
        assertTrue(approved.decidedAt!! >= before, "${approved.decidedAt}")
        assertNull(approved.rejectReason)
        val saved = projectRepository.findById(project.id!!).orElseThrow()
        assertNotNull(saved.keyHash)
        assertEquals(approved.decidedAt, saved.issuedAt)
    }

    @Test
    fun `이미 키가 있는 프로젝트면 승인해도 키는 그대로고 승인된 분야가 바로 열린다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, KEY, ApiKeyField.CLUB)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)
        mockMvc.get("/students") { header("X-API-Key", KEY) }.andExpect { status { isForbidden() } }

        mockMvc.post("/admin/api-applications/${application.id}/approve") { bearer(adminToken) }.andExpect { status { isOk() } }

        assertEquals(ApiKeySecret.hash(KEY), projectRepository.findById(project.id!!).orElseThrow().keyHash)
        mockMvc.get("/students") { header("X-API-Key", KEY) }.andExpect { status { isOk() } }
    }

    @Test
    fun `대기 중이 아닌 신청을 승인하거나 반려하면 APPLICATION_ALREADY_DECIDED`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, KEY)
        val approved = fieldRepository.saveField(project.id!!, ApiKeyField.CLUB, ApplicationStatus.APPROVED)
        val rejected = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.REJECTED)

        listOf(approved, rejected).forEach { application ->
            listOf(
                mockMvc.post("/admin/api-applications/${application.id}/approve") { bearer(adminToken) },
                mockMvc.post("/admin/api-applications/${application.id}/reject") { bearer(adminToken); reason("사유") },
            ).forEach {
                it.andExpect {
                    status { isConflict() }
                    jsonPath("$.code") { value("APPLICATION_ALREADY_DECIDED") }
                    jsonPath("$.message") { value("이미 처리된 신청입니다.") }
                }
            }
        }
        assertEquals(ApplicationStatus.REJECTED, fieldRepository.findById(rejected.id!!).orElseThrow().status)
        assertEquals(ApplicationStatus.APPROVED, fieldRepository.findById(approved.id!!).orElseThrow().status)
    }

    // 반려

    @Test
    fun `반려하면 사유와 처리 기록을 남기고 키는 발급하지 않는다`() {
        val project = projectRepository.saveProject(userId)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING)

        mockMvc.post("/admin/api-applications/${application.id}/reject") {
            bearer(adminToken)
            reason("사용 목적에 교직원 정보가 필요하지 않습니다.")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.rejectReason") { value("사용 목적에 교직원 정보가 필요하지 않습니다.") }
            jsonPath("$.decidedBy") { value(ADMIN_ID) }
        }

        val rejected = fieldRepository.findById(application.id!!).orElseThrow()
        assertEquals(ApplicationStatus.REJECTED, rejected.status)
        assertEquals("사용 목적에 교직원 정보가 필요하지 않습니다.", rejected.rejectReason)
        assertEquals(ADMIN_ID, rejected.decidedBy)
        assertNotNull(rejected.decidedAt)
        assertNull(projectRepository.findById(project.id!!).orElseThrow().keyHash)
    }

    @Test
    fun `반려 사유는 앞뒤 공백을 다듬어 저장한다`() {
        val project = projectRepository.saveProject(userId)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING)

        mockMvc.post("/admin/api-applications/${application.id}/reject") { bearer(adminToken); reason("  목적과 맞지 않습니다.  ") }
            .andExpect {
                status { isOk() }
                jsonPath("$.rejectReason") { value("목적과 맞지 않습니다.") }
            }
        assertEquals("목적과 맞지 않습니다.", fieldRepository.findById(application.id!!).orElseThrow().rejectReason)
    }

    @Test
    fun `반려 사유는 1자에서 200자까지 받는다`() {
        val project = projectRepository.saveProject(userId)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING)

        listOf(" ", "", "가".repeat(201)).forEach { reason ->
            mockMvc.post("/admin/api-applications/${application.id}/reject") { bearer(adminToken); reason(reason) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString("reason")) }
            }
        }
        mockMvc.post("/admin/api-applications/${application.id}/reject") {
            bearer(adminToken)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isBadRequest() } }
        assertEquals(ApplicationStatus.PENDING, fieldRepository.findById(application.id!!).orElseThrow().status)

        mockMvc.post("/admin/api-applications/${application.id}/reject") { bearer(adminToken); reason("가".repeat(200)) }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `반려된 분야를 사용자가 다시 신청하면 다시 승인할 수 있다`() {
        val project = projectRepository.saveProject(userId)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING)
        mockMvc.post("/admin/api-applications/${application.id}/reject") { bearer(adminToken); reason("사유") }
        val rejected = fieldRepository.findById(application.id!!).orElseThrow()
        rejected.resubmit("다시 신청한 목적", LocalDateTime.now(SEOUL))
        fieldRepository.save(rejected)

        mockMvc.post("/admin/api-applications/${application.id}/approve") { bearer(adminToken) }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.rejectReason") { value(nullValue()) }
        }
    }

    // 권한

    @Test
    fun `USER는 FORBIDDEN, 토큰이 없으면 INVALID_TOKEN`() {
        val project = projectRepository.saveProject(userId)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING)
        val requests = listOf<(String?) -> ResultActionsDsl>(
            { token -> mockMvc.get("/admin/api-applications") { token?.let { bearer(it) } } },
            { token -> mockMvc.get("/admin/api-applications/${application.id}") { token?.let { bearer(it) } } },
            { token -> mockMvc.post("/admin/api-applications/${application.id}/approve") { token?.let { bearer(it) } } },
            { token -> mockMvc.post("/admin/api-applications/${application.id}/reject") { token?.let { bearer(it) }; reason("사유") } },
        )

        requests.forEach { request ->
            request(userToken).andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
            request(null).andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
        assertEquals(ApplicationStatus.PENDING, fieldRepository.findById(application.id!!).orElseThrow().status)
    }

    private fun field(
        projectId: Long,
        field: ApiKeyField,
        status: ApplicationStatus,
        appliedAt: LocalDateTime,
        type: ApplicationType = ApplicationType.FIRST,
    ): ApiProjectField {
        val saved = fieldRepository.saveField(projectId, field, status)
        ReflectionTestUtils.setField(saved, "appliedAt", appliedAt)
        ReflectionTestUtils.setField(saved, "applicationType", type)
        return fieldRepository.save(saved)
    }

    private fun MockHttpServletRequestDsl.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun MockHttpServletRequestDsl.reason(reason: String) {
        contentType = MediaType.APPLICATION_JSON
        content = """{"reason":"$reason"}"""
    }

    companion object {
        private const val ADMIN_ID = 1L
        private const val KEY = "xq_admin-test-key"
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
