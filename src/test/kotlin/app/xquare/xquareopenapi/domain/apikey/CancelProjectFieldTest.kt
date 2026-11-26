package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class CancelProjectFieldTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
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
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `대기 중인 신청을 취소하면 그 신청만 지우고 다른 분야와 키는 그대로 둔다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, RAW_KEY, ApiKeyField.SCHEDULE)
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)
        fieldRepository.saveField(project.id!!, ApiKeyField.TEACHER, ApplicationStatus.PENDING)

        cancel(project.id!!, "STUDENT").andExpect {
            status { isOk() }
            jsonPath("$.message") { value("취소되었습니다.") }
        }

        assertEquals(
            listOf(ApiKeyField.SCHEDULE, ApiKeyField.TEACHER),
            fieldRepository.findAllByProjectIdOrderByIdAsc(project.id!!).map { it.field },
        )
        assertEquals(ApiKeySecret.hash(RAW_KEY), projectRepository.findById(project.id!!).orElseThrow().keyHash)
    }

    @Test
    fun `승인되거나 반려된 신청은 APPLICATION_NOT_PENDING이고 지우지 않는다`() {
        val project = projectRepository.saveProject(userId)
        fieldRepository.saveField(project.id!!, ApiKeyField.SCHEDULE, ApplicationStatus.APPROVED)
        fieldRepository.saveField(project.id!!, ApiKeyField.CLUB, ApplicationStatus.REJECTED)

        listOf("SCHEDULE", "CLUB").forEach { field ->
            cancel(project.id!!, field).andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("APPLICATION_NOT_PENDING") }
                jsonPath("$.message") { value("승인 대기 중인 신청만 취소할 수 있습니다.") }
            }
        }
        assertEquals(2, fieldRepository.count())
    }

    @Test
    fun `신청하지 않은 분야는 APPLICATION_NOT_FOUND`() {
        val project = projectRepository.saveProject(userId)
        fieldRepository.saveField(project.id!!, ApiKeyField.CLUB, ApplicationStatus.PENDING)

        cancel(project.id!!, "STUDENT").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("APPLICATION_NOT_FOUND") }
        }
        assertEquals(1, fieldRepository.count())
    }

    @Test
    fun `다른 프로젝트의 같은 분야 신청은 건드리지 않는다`() {
        val mine = projectRepository.saveProject(userId, name = "내 프로젝트")
        val other = projectRepository.saveProject(userId, name = "다른 프로젝트")
        fieldRepository.saveField(other.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        cancel(mine.id!!, "STUDENT").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("APPLICATION_NOT_FOUND") }
        }
        assertEquals(1, fieldRepository.count())
    }

    @Test
    fun `없거나 남의 프로젝트면 PROJECT_NOT_FOUND이고 남의 신청은 지우지 않는다`() {
        val stranger = userRepository.saveUser("other")
        val othersProject = projectRepository.saveProject(stranger.id!!)
        fieldRepository.saveField(othersProject.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        listOf(othersProject.id!!, othersProject.id!! + 100).forEach { projectId ->
            cancel(projectId, "STUDENT").andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PROJECT_NOT_FOUND") }
            }
        }
        assertEquals(1, fieldRepository.count())
    }

    @Test
    fun `취소한 분야는 다시 신청할 수 있도록 기록이 남지 않는다`() {
        val project = projectRepository.saveProject(userId)
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        cancel(project.id!!, "STUDENT").andExpect { status { isOk() } }
        cancel(project.id!!, "STUDENT").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("APPLICATION_NOT_FOUND") }
        }
        assertEquals(0, fieldRepository.count())
    }

    @Test
    fun `없는 분야 이름이면 INVALID_INPUT`() {
        val project = projectRepository.saveProject(userId)

        cancel(project.id!!, "TIMETABLE").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    @Test
    fun `액세스 토큰이 없거나 API 키로는 취소할 수 없다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, RAW_KEY, ApiKeyField.SCHEDULE)
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        mockMvc.delete("/projects/${project.id}/fields/STUDENT") { header("X-API-Key", RAW_KEY) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
        assertEquals(2, fieldRepository.count())
    }

    private fun cancel(projectId: Long, field: String) =
        mockMvc.delete("/projects/$projectId/fields/$field") { header("Authorization", "Bearer $userToken") }

    companion object {
        private const val RAW_KEY = "xq_cancel-test"
    }
}
