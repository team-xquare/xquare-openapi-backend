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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class UpdateProjectTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
) {
    // 이름 검사를 지나친 동시 수정이나 저장 실패를 흉내 낼 때 쓴다.
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
    fun `이름과 서비스 URL을 바꾸고 분야와 키는 그대로 둔다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, RAW_KEY, ApiKeyField.SCHEDULE)

        update(project.id!!, """{"name":"새 이름","serviceUrl":"https://meal.example.com/app"}""").andExpect {
            status { isOk() }
            jsonPath("$.projectId") { value(project.id!!) }
            jsonPath("$.name") { value("새 이름") }
            jsonPath("$.serviceUrl") { value("https://meal.example.com/app") }
            jsonPath("$.keyIssued") { value(true) }
            jsonPath("$.fields[0].field") { value("SCHEDULE") }
        }

        val saved = projectRepository.findById(project.id!!).orElseThrow()
        assertEquals("새 이름", saved.name)
        assertEquals("https://meal.example.com/app", saved.serviceUrl)
        assertEquals(ApiKeySecret.hash(RAW_KEY), saved.keyHash)
        assertEquals(1, fieldRepository.count())
        mockMvc.get("/projects") { header("Authorization", "Bearer $userToken") }.andExpect {
            jsonPath("$[0].serviceUrl") { value("https://meal.example.com/app") }
        }
    }

    @Test
    fun `서비스 URL을 빼거나 null로 보내면 지운다`() {
        val project = projectRepository.saveProject(userId, name = "급식 알리미")
        update(project.id!!, """{"name":"급식 알리미","serviceUrl":"http://a.b"}""").andExpect { status { isOk() } }

        update(project.id!!, """{"name":"급식 알리미"}""").andExpect {
            status { isOk() }
            jsonPath("$.serviceUrl") { value(nullValue()) }
        }
        assertNull(projectRepository.findById(project.id!!).orElseThrow().serviceUrl)
        update(project.id!!, """{"name":"급식 알리미","serviceUrl":"http://a.b"}""").andExpect { status { isOk() } }
        update(project.id!!, """{"name":"급식 알리미","serviceUrl":null}""").andExpect { status { isOk() } }
        assertNull(projectRepository.findById(project.id!!).orElseThrow().serviceUrl)
    }

    @Test
    fun `이름을 그대로 두거나 앞뒤 공백만 붙여 보내도 자기 이름과 겹치는 것은 막지 않는다`() {
        val project = projectRepository.saveProject(userId, name = "급식 알리미")

        update(project.id!!, """{"name":" 급식 알리미 "}""").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("급식 알리미") }
        }
        assertEquals("급식 알리미", projectRepository.findById(project.id!!).orElseThrow().name)
    }

    @Test
    fun `내 다른 프로젝트와 이름이 같으면 PROJECT_NAME_DUPLICATED이고 바꾸지 않는다`() {
        projectRepository.saveProject(userId, name = "다른 프로젝트")
        val project = projectRepository.saveProject(userId, name = "급식 알리미")

        update(project.id!!, """{"name":"다른 프로젝트","serviceUrl":"https://a.b"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PROJECT_NAME_DUPLICATED") }
        }
        val saved = projectRepository.findById(project.id!!).orElseThrow()
        assertEquals("급식 알리미", saved.name)
        assertNull(saved.serviceUrl)
    }

    @Test
    fun `다른 사용자의 프로젝트와는 이름이 같아도 된다`() {
        val other = userRepository.saveUser("other")
        projectRepository.saveProject(other.id!!, name = "다른 프로젝트")
        val project = projectRepository.saveProject(userId, name = "급식 알리미")

        update(project.id!!, """{"name":"다른 프로젝트"}""").andExpect { status { isOk() } }
    }

    @Test
    fun `이름 검사를 지나쳐 이름 제약에 걸려도 PROJECT_NAME_DUPLICATED`() {
        projectRepository.saveProject(userId, name = "다른 프로젝트")
        val project = projectRepository.saveProject(userId, name = "급식 알리미")
        doReturn(false).`when`(spiedProjectRepository).existsByUserIdAndName(anyLong(), anyString())

        update(project.id!!, """{"name":"다른 프로젝트"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PROJECT_NAME_DUPLICATED") }
        }
        assertEquals("급식 알리미", projectRepository.findById(project.id!!).orElseThrow().name)
    }

    @Test
    fun `이름 제약이 아닌 무결성 위반은 409로 바꾸지 않고 500 INTERNAL_SERVER_ERROR`() {
        val project = projectRepository.saveProject(userId, name = "급식 알리미")
        doThrow(DataIntegrityViolationException("다른 제약 위반")).`when`(spiedProjectRepository).flush()

        update(project.id!!, """{"name":"새 이름"}""").andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_SERVER_ERROR") }
        }
    }

    @Test
    fun `없거나 남의 프로젝트면 PROJECT_NOT_FOUND`() {
        val other = userRepository.saveUser("other")
        val othersProject = projectRepository.saveProject(other.id!!, name = "남의 프로젝트")

        listOf(othersProject.id!!, othersProject.id!! + 100).forEach { projectId ->
            update(projectId, """{"name":"새 이름"}""").andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PROJECT_NOT_FOUND") }
            }
        }
        assertEquals("남의 프로젝트", projectRepository.findById(othersProject.id!!).orElseThrow().name)
    }

    @Test
    fun `이름은 30자, 서비스 URL은 255자까지 받는다`() {
        val project = projectRepository.saveProject(userId)
        val url = "https://a.b/" + "a".repeat(255 - "https://a.b/".length)

        update(project.id!!, """{"name":"${"가".repeat(30)}","serviceUrl":"$url"}""").andExpect { status { isOk() } }
        assertEquals(url, projectRepository.findById(project.id!!).orElseThrow().serviceUrl)
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        val project = projectRepository.saveProject(userId, name = "급식 알리미")

        listOf(
            """{"name":" "}""" to "name",
            """{"name":"${"가".repeat(31)}"}""" to "name",
            """{"name":"a","serviceUrl":"ftp://a.b"}""" to "serviceUrl",
            """{"name":"a","serviceUrl":"meal.example.com"}""" to "serviceUrl",
            """{"name":"a","serviceUrl":"https://a b"}""" to "serviceUrl",
            """{"name":"a","serviceUrl":"https://"}""" to "serviceUrl",
            """{"name":"a","serviceUrl":""}""" to "serviceUrl",
            """{"name":"a","serviceUrl":"https://a.b/${"a".repeat(244)}"}""" to "serviceUrl",
            "{}" to "요청 값이 올바르지 않습니다.",
        ).forEach { (invalid, expectedMessage) ->
            update(project.id!!, invalid).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals("급식 알리미", projectRepository.findById(project.id!!).orElseThrow().name)
    }

    @Test
    fun `만들 때도 서비스 URL을 받는다`() {
        mockMvc.post("/projects") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"급식 알리미","serviceUrl":"https://meal.example.com"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.serviceUrl") { value("https://meal.example.com") }
        }
        assertEquals("https://meal.example.com", projectRepository.findAll().single().serviceUrl)
    }

    @Test
    fun `액세스 토큰이 없거나 API 키로는 수정할 수 없다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, RAW_KEY, ApiKeyField.SCHEDULE)

        mockMvc.patch("/projects/${project.id}") {
            header("X-API-Key", RAW_KEY)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"새 이름"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_TOKEN") }
        }
    }

    private fun update(projectId: Long, body: String) =
        mockMvc.patch("/projects/$projectId") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    companion object {
        private const val RAW_KEY = "xq_update-test"
    }
}
