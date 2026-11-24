package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyAuthenticationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val studentRepository: StudentRepository,
    jwtProvider: JwtProvider,
) {
    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)

    @AfterEach
    fun tearDown() {
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
        studentRepository.deleteAll()
    }

    @Test
    fun `승인된 분야의 조회 API만 열리고 승인되지 않은 분야는 API_KEY_FIELD_FORBIDDEN`() {
        val project = saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.STUDENT)

        mockMvc.get("/students") { header("X-API-Key", KEY) }.andExpect { status { isOk() } }
        listOf("/teachers", "/teachers/999").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", KEY) }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("API_KEY_FIELD_FORBIDDEN") }
                jsonPath("$.message") { value("API 키에 승인되지 않은 분야입니다. 해당 분야를 신청해 승인을 받아 주세요.") }
            }
        }
        assertEquals(1, fieldRepository.findAllByProjectIdOrderByIdAsc(project.id!!).size)
    }

    @Test
    fun `승인 대기나 반려된 분야는 신청했어도 조회할 수 없다`() {
        val project = saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.CLUB)
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)
        fieldRepository.saveField(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.REJECTED)

        listOf("/students", "/students/1", "/teachers", "/teachers/1").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", KEY) }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("API_KEY_FIELD_FORBIDDEN") }
            }
        }
    }

    @Test
    fun `키는 자기 프로젝트의 분야만 연다`() {
        saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.STUDENT)
        saveApiKey(projectRepository, fieldRepository, 2L, OTHER_KEY, ApiKeyField.TEACHER)

        mockMvc.get("/teachers") { header("X-API-Key", OTHER_KEY) }.andExpect { status { isOk() } }
        mockMvc.get("/teachers") { header("X-API-Key", KEY) }.andExpect { status { isForbidden() } }
        mockMvc.get("/students") { header("X-API-Key", OTHER_KEY) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `교내 분야 키로 동아리, 자습 감독, 일정을 조회하고 다른 교내 분야는 승인돼야 열린다`() {
        saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.CLUB, ApiKeyField.STUDY_SUPERVISOR)

        mockMvc.get("/clubs") { header("X-API-Key", KEY) }.andExpect { status { isOk() } }
        mockMvc.get("/clubs/999") { header("X-API-Key", KEY) }.andExpect { jsonPath("$.code") { value("CLUB_NOT_FOUND") } }
        mockMvc.get("/study-supervisors?startDate=2026-09-14&finishDate=2026-09-14") { header("X-API-Key", KEY) }
            .andExpect { status { isOk() } }
        mockMvc.get("/study-supervisors/day?date=2026-09-14") { header("X-API-Key", KEY) }.andExpect { status { isOk() } }
        listOf("/schedules/999", "/schedules/999/anything").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", KEY) }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("API_KEY_FIELD_FORBIDDEN") }
            }
        }
    }

    @Test
    fun `일정 분야 키로 일정을 조회한다`() {
        saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.SCHEDULE)

        mockMvc.get("/schedules/999") { header("X-API-Key", KEY) }.andExpect { jsonPath("$.code") { value("SCHEDULE_NOT_FOUND") } }
        listOf("/clubs", "/study-supervisors/day?date=2026-09-14").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", KEY) }.andExpect { status { isForbidden() } }
        }
    }

    @Test
    fun `만료된 키는 API_KEY_EXPIRED이고 만료 전이면 조회된다`() {
        val project = saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.STUDENT)
        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

        ReflectionTestUtils.setField(project, "expiresAt", now.plusHours(1))
        projectRepository.save(project)
        mockMvc.get("/students") { header("X-API-Key", KEY) }.andExpect { status { isOk() } }

        ReflectionTestUtils.setField(project, "expiresAt", now.minusSeconds(1))
        projectRepository.save(project)
        listOf("/students", "/teachers").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", KEY) }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("API_KEY_EXPIRED") }
                jsonPath("$.message") { value("만료된 API 키입니다. 키를 갱신해 주세요.") }
            }
        }
    }

    @Test
    fun `학생 분야 키로 학생 단건 조회도 된다`() {
        saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.STUDENT)
        val student = studentRepository.save(Student(2315, "김준서", LocalDate.of(2009, 7, 28), "s@dsm.hs.kr"))

        mockMvc.get("/students/${student.id}") { header("X-API-Key", KEY) }.andExpect { status { isOk() } }
    }

    @Test
    fun `키가 없거나 어느 프로젝트의 키도 아니면 액세스 토큰이 있어도 INVALID_API_KEY`() {
        saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.STUDENT)

        mockMvc.get("/students") { header("Authorization", "Bearer $adminToken") }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_API_KEY") }
        }
        mockMvc.get("/students") { header("X-API-Key", "xq_unknown") }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_API_KEY") }
        }
    }

    @Test
    fun `키로는 등록, 삭제나 프로젝트 API를 쓸 수 없다`() {
        saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.STUDENT)
        val student = studentRepository.save(Student(2315, "김준서", LocalDate.of(2009, 7, 28), "s@dsm.hs.kr"))

        listOf(
            mockMvc.post("/students") {
                header("X-API-Key", KEY)
                contentType = MediaType.APPLICATION_JSON
                content = """{"studentNumber":2316,"name":"김","birthDate":"2009-07-28","email":"a@dsm.hs.kr"}"""
            },
            mockMvc.delete("/students/${student.id}") { header("X-API-Key", KEY) },
            mockMvc.post("/projects") {
                header("X-API-Key", KEY)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"새 프로젝트","purpose":"목적","fields":["CLUB"],"emailVerifiedToken":"t"}"""
            },
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
        assertEquals(1, studentRepository.count())
        assertEquals(1, projectRepository.count())
    }

    @Test
    fun `HEAD 요청도 토큰만으로는 조회할 수 없고 분야가 승인된 키로만 된다`() {
        saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.STUDENT)
        val student = studentRepository.save(Student(2315, "김준서", LocalDate.of(2009, 7, 28), "s@dsm.hs.kr"))

        listOf("/students", "/students/${student.id}", "/teachers", "/teachers/999").forEach { path ->
            mockMvc.perform(head(path).header("Authorization", "Bearer $adminToken"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
        mockMvc.perform(head("/students/${student.id}").header("X-API-Key", KEY))
            .andExpect(MockMvcResultMatchers.status().isOk)
        mockMvc.perform(head("/teachers").header("X-API-Key", KEY))
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    fun `토큰과 키를 같이 보내도 조회는 키로, 등록은 토큰으로 인증한다`() {
        saveApiKey(projectRepository, fieldRepository, 2L, KEY, ApiKeyField.STUDENT)

        mockMvc.get("/students") {
            header("Authorization", "Bearer $adminToken")
            header("X-API-Key", KEY)
        }.andExpect { status { isOk() } }
        mockMvc.get("/teachers") {
            header("Authorization", "Bearer $adminToken")
            header("X-API-Key", KEY)
        }.andExpect { status { isForbidden() } }
        mockMvc.post("/students") {
            header("Authorization", "Bearer $adminToken")
            header("X-API-Key", KEY)
            contentType = MediaType.APPLICATION_JSON
            content = """{"studentNumber":2316,"name":"김","birthDate":"2009-07-28","email":"a@dsm.hs.kr"}"""
        }.andExpect { status { isCreated() } }
    }

    companion object {
        private const val KEY = "xq_project-key"
        private const val OTHER_KEY = "xq_other-project-key"
    }
}
