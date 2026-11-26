package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
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
import org.springframework.test.web.servlet.post
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
    }

    @AfterEach
    fun tearDown() {
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `이름만으로 빈 프로젝트를 만들고 키와 분야는 없다`() {
        val before = LocalDateTime.now(SEOUL)
        create(body()).andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("급식 알리미") }
            jsonPath("$.keyIssued") { value(false) }
            jsonPath("$.issuedAt") { value(nullValue()) }
            jsonPath("$.expiresAt") { value(nullValue()) }
            jsonPath("$.fields.length()") { value(0) }
            jsonPath("$.createdAt") { value(containsString("+09:00")) }
        }
        val after = LocalDateTime.now(SEOUL)

        val project = projectRepository.findAll().single()
        assertEquals(userId, project.userId)
        assertEquals("급식 알리미", project.name)
        assertNull(project.keyHash)
        assertNull(project.issuedAt)
        assertNull(project.expiresAt)
        assertTrue(project.createdAt in before..after, "${project.createdAt}")
        assertEquals(0, fieldRepository.count())
    }

    @Test
    fun `응답의 projectId는 저장된 프로젝트의 id다`() {
        create(body()).andExpect { status { isCreated() } }.andReturn().response.contentAsString.let { json ->
            assertTrue(json.contains("\"projectId\":${projectRepository.findAll().single().id}"), json)
        }
    }

    @Test
    fun `같은 이름의 내 프로젝트가 있으면 PROJECT_NAME_DUPLICATED`() {
        projectRepository.saveProject(userId, name = "급식 알리미")

        create(body()).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PROJECT_NAME_DUPLICATED") }
            jsonPath("$.message") { value("같은 이름의 프로젝트가 이미 있습니다.") }
        }
        assertEquals(1, projectRepository.count())
    }

    @Test
    fun `이름은 앞뒤 공백을 다듬어 저장한다`() {
        create(body(name = " 급식 알리미 ")).andExpect { status { isCreated() } }

        assertEquals("급식 알리미", projectRepository.findAll().single().name)
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
        create(body(name = "여섯 번째")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PROJECT_LIMIT_EXCEEDED") }
            jsonPath("$.message") { value("프로젝트는 최대 5개까지 만들 수 있습니다.") }
        }
        assertEquals(5, projectRepository.countByUserId(userId))
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
    }

    @Test
    fun `이름 제약이 아닌 무결성 위반은 409로 바꾸지 않고 500 INTERNAL_SERVER_ERROR`() {
        doThrow(DataIntegrityViolationException("다른 제약 위반")).`when`(spiedProjectRepository).saveAndFlush(any())

        create(body()).andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_SERVER_ERROR") }
        }
    }

    @Test
    fun `토큰의 사용자가 없으면 INVALID_TOKEN이고 아무것도 만들지 않는다`() {
        create(body(), jwtProvider.generateAccessToken(userId + 100, Role.USER)).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
        assertEquals(0, projectRepository.count())
    }

    @Test
    fun `이메일 인증 토큰 없이 만들 수 있고, 예전처럼 분야를 같이 보내도 무시한다`() {
        create("""{"name":"급식 알리미","purpose":"목적","fields":["SCHEDULE"]}""").andExpect {
            status { isCreated() }
            jsonPath("$.fields.length()") { value(0) }
        }
        assertEquals(0, fieldRepository.count())
        assertNull(projectRepository.findAll().single().keyHash)
    }

    @Test
    fun `이름은 30자까지 받는다`() {
        create(body(name = "가".repeat(30))).andExpect { status { isCreated() } }
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        listOf(
            body(name = " ") to "name",
            body(name = "가".repeat(31)) to "name",
            "{}" to "요청 값이 올바르지 않습니다.",
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

    private fun body(name: String = "급식 알리미") = """{"name":"$name"}"""

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
