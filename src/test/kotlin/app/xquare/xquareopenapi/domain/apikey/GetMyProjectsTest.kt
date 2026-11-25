package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class GetMyProjectsTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    jwtProvider: JwtProvider,
) {
    private val userToken = jwtProvider.generateAccessToken(USER_ID, Role.USER)

    @AfterEach
    fun tearDown() {
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
    }

    @Test
    fun `내 프로젝트를 만든 순서대로 키 발급 여부, 시각, 분야별 상태와 함께 보여 준다`() {
        val issued = projectRepository.saveProject(USER_ID, name = "급식 알리미", rawKey = "xq_first")
        ReflectionTestUtils.setField(issued, "issuedAt", LocalDateTime.of(2026, 11, 24, 10, 0))
        ReflectionTestUtils.setField(issued, "expiresAt", LocalDateTime.of(2027, 1, 23, 10, 0))
        projectRepository.save(issued)
        fieldRepository.saveField(issued.id!!, ApiKeyField.SCHEDULE)
        val rejected = fieldRepository.saveField(issued.id!!, ApiKeyField.STUDENT, ApplicationStatus.REJECTED)
        ReflectionTestUtils.setField(rejected, "rejectReason", "사용 목적에 학생 개인정보가 필요하지 않습니다.")
        fieldRepository.save(rejected)
        val pending = projectRepository.saveProject(USER_ID, name = "선생님 찾기")
        fieldRepository.saveField(pending.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING)
        // 남의 프로젝트는 보이지 않는다.
        projectRepository.saveProject(OTHER_USER_ID, name = "남의 프로젝트", rawKey = "xq_other")

        mockMvc.get("/projects") { header("Authorization", "Bearer $userToken") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].projectId") { value(issued.id!!) }
            jsonPath("$[0].name") { value("급식 알리미") }
            jsonPath("$[0].keyIssued") { value(true) }
            jsonPath("$[0].issuedAt") { value("2026-11-24T10:00:00+09:00") }
            jsonPath("$[0].expiresAt") { value("2027-01-23T10:00:00+09:00") }
            jsonPath("$[0].fields.length()") { value(2) }
            jsonPath("$[0].fields[0].field") { value("SCHEDULE") }
            jsonPath("$[0].fields[0].level") { value("SCHOOL") }
            jsonPath("$[0].fields[0].status") { value("APPROVED") }
            jsonPath("$[0].fields[0].rejectReason") { value(nullValue()) }
            jsonPath("$[0].fields[1].field") { value("STUDENT") }
            jsonPath("$[0].fields[1].level") { value("RESTRICTED") }
            jsonPath("$[0].fields[1].status") { value("REJECTED") }
            jsonPath("$[0].fields[1].rejectReason") { value("사용 목적에 학생 개인정보가 필요하지 않습니다.") }
            // 키 원문이나 해시는 내려주지 않는다.
            jsonPath("$[0].apiKey") { doesNotExist() }
            jsonPath("$[0].keyHash") { doesNotExist() }
            jsonPath("$[1].name") { value("선생님 찾기") }
            jsonPath("$[1].keyIssued") { value(false) }
            jsonPath("$[1].issuedAt") { value(nullValue()) }
            jsonPath("$[1].expiresAt") { value(nullValue()) }
            jsonPath("$[1].fields[0].status") { value("PENDING") }
        }
    }

    @Test
    fun `프로젝트가 없으면 빈 배열이다`() {
        mockMvc.get("/projects") { header("Authorization", "Bearer $userToken") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `상세는 사용 목적과 만든 시각까지 보여 준다`() {
        val project = projectRepository.saveProject(USER_ID, name = "급식 알리미")
        ReflectionTestUtils.setField(project, "createdAt", LocalDateTime.of(2026, 11, 24, 9, 30))
        projectRepository.save(project)
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        mockMvc.get("/projects/${project.id}") { header("Authorization", "Bearer $userToken") }.andExpect {
            status { isOk() }
            jsonPath("$.projectId") { value(project.id!!) }
            jsonPath("$.name") { value("급식 알리미") }
            jsonPath("$.purpose") { value("테스트 목적") }
            jsonPath("$.createdAt") { value("2026-11-24T09:30:00+09:00") }
            jsonPath("$.keyIssued") { value(false) }
            jsonPath("$.issuedAt") { value(nullValue()) }
            jsonPath("$.expiresAt") { value(nullValue()) }
            jsonPath("$.fields[0].field") { value("STUDENT") }
            jsonPath("$.fields[0].status") { value("PENDING") }
            jsonPath("$.fields[0].rejectReason") { value(nullValue()) }
        }
    }

    @Test
    fun `없거나 남의 프로젝트 상세는 PROJECT_NOT_FOUND`() {
        val others = projectRepository.saveProject(OTHER_USER_ID)

        listOf(others.id!!, others.id!! + 100).forEach { projectId ->
            mockMvc.get("/projects/$projectId") { header("Authorization", "Bearer $userToken") }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PROJECT_NOT_FOUND") }
            }
        }
    }

    @Test
    fun `프로젝트가 없으면 빈 목록이다`() {
        mockMvc.get("/projects") { header("Authorization", "Bearer $userToken") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `projectId가 숫자가 아니면 INVALID_INPUT`() {
        mockMvc.get("/projects/abc") { header("Authorization", "Bearer $userToken") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    @Test
    fun `액세스 토큰이 필요하고 API 키로는 볼 수 없다`() {
        saveApiKey(projectRepository, fieldRepository, USER_ID, "xq_key", ApiKeyField.CLUB)

        listOf("/projects", "/projects/1").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", "xq_key") }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
    }

    companion object {
        private const val USER_ID = 2L
        private const val OTHER_USER_ID = 3L
    }
}
