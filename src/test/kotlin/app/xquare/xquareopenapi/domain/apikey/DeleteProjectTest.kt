package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class DeleteProjectTest @Autowired constructor(
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
    fun `삭제하면 프로젝트와 분야를 지우고 키는 바로 못 쓴다`() {
        val project = saveApiKey(projectRepository, fieldRepository, USER_ID, KEY, ApiKeyField.CLUB)
        fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)
        // 다른 프로젝트는 그대로 남는다.
        val kept = saveApiKey(projectRepository, fieldRepository, USER_ID, OTHER_KEY, ApiKeyField.SCHEDULE)

        mockMvc.delete("/projects/${project.id}") { header("Authorization", "Bearer $userToken") }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("삭제되었습니다.") }
        }

        assertEquals(listOf(kept.id), projectRepository.findAll().map { it.id })
        assertEquals(listOf(ApiKeyField.SCHEDULE), fieldRepository.findAll().map { it.field })
        mockMvc.get("/clubs") { header("X-API-Key", KEY) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_API_KEY") }
        }
    }

    @Test
    fun `없거나 남의 프로젝트면 PROJECT_NOT_FOUND이고 아무것도 지우지 않는다`() {
        val others = saveApiKey(projectRepository, fieldRepository, OTHER_USER_ID, KEY, ApiKeyField.CLUB)

        listOf(others.id!!, others.id!! + 100).forEach { projectId ->
            mockMvc.delete("/projects/$projectId") { header("Authorization", "Bearer $userToken") }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PROJECT_NOT_FOUND") }
            }
        }
        assertEquals(1, projectRepository.count())
        assertEquals(1, fieldRepository.count())
    }

    @Test
    fun `지운 뒤 같은 이름으로 다시 만들 수 있게 이름도 비운다`() {
        val project = projectRepository.saveProject(USER_ID, name = "급식 알리미")

        mockMvc.delete("/projects/${project.id}") { header("Authorization", "Bearer $userToken") }.andExpect { status { isOk() } }

        assertEquals(false, projectRepository.existsByUserIdAndName(USER_ID, "급식 알리미"))
    }

    @Test
    fun `액세스 토큰이 필요하다`() {
        val project = saveApiKey(projectRepository, fieldRepository, USER_ID, KEY, ApiKeyField.CLUB)

        mockMvc.delete("/projects/${project.id}") { header("X-API-Key", KEY) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
        assertEquals(1, projectRepository.count())
    }

    companion object {
        private const val USER_ID = 2L
        private const val OTHER_USER_ID = 3L
        private const val KEY = "xq_delete-key"
        private const val OTHER_KEY = "xq_kept-key"
    }
}
