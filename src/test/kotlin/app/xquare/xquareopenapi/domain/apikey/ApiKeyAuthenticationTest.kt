package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import app.xquare.xquareopenapi.domain.auth.FakeEmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.student.StudentRepository
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class ApiKeyAuthenticationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
    private val studentRepository: StudentRepository,
    private val userRepository: UserRepository,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
    private val jsonMapper: JsonMapper,
    private val jwtProvider: JwtProvider,
) {
    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.saveUser("junseo")
        userToken = jwtProvider.generateAccessToken(user.id!!, Role.USER)
    }

    @AfterEach
    fun tearDown() {
        apiKeyApplicationRepository.deleteAll()
        apiKeyRepository.deleteAll()
        studentRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @Test
    fun `신청한 분야의 조회 API만 열리고 다른 분야는 API_KEY_FIELD_FORBIDDEN`() {
        val apiKey = apply("STUDENT")

        mockMvc.get("/students") { header("X-API-Key", apiKey) }.andExpect { status { isOk() } }
        listOf("/teachers", "/teachers/999").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", apiKey) }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("API_KEY_FIELD_FORBIDDEN") }
                jsonPath("$.message") { value("API 키로 신청하지 않은 분야입니다. 해당 분야를 활용신청해 주세요.") }
            }
        }
    }

    @Test
    fun `분야를 더하면 같은 키로 그 분야도 조회할 수 있다`() {
        val apiKey = apply("STUDENT")

        apply("TEACHER")

        listOf("/students", "/teachers").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", apiKey) }.andExpect { status { isOk() } }
        }
    }

    @Test
    fun `학생 분야 키로 학생 단건 조회도 된다`() {
        val apiKey = apply("STUDENT")
        val student = studentRepository.save(Student(2315, "김준서", LocalDate.of(2009, 7, 28), "s@dsm.hs.kr"))

        mockMvc.get("/students/${student.id}") { header("X-API-Key", apiKey) }.andExpect { status { isOk() } }
    }

    @Test
    fun `교직원 분야만 신청한 키로는 학생을 조회할 수 없다`() {
        val apiKey = apply("TEACHER")

        mockMvc.get("/teachers") { header("X-API-Key", apiKey) }.andExpect { status { isOk() } }
        listOf("/students", "/students/1").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", apiKey) }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("API_KEY_FIELD_FORBIDDEN") }
            }
        }
    }

    @Test
    fun `재발급하면 이전 키는 바로 못 쓰고 새 키는 분야를 그대로 쓴다`() {
        val oldKey = apply("STUDENT")
        emailVerifiedTokens.tokens["reissue-token"] = EMAIL

        val newKey = jsonMapper.readTree(
            mockMvc.post("/api-keys/reissue") {
                header("Authorization", "Bearer $userToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"emailVerifiedToken":"reissue-token"}"""
            }.andReturn().response.contentAsString,
        )["apiKey"].asString()

        mockMvc.get("/students") { header("X-API-Key", oldKey) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_API_KEY") }
        }
        mockMvc.get("/students") { header("X-API-Key", newKey) }.andExpect { status { isOk() } }
        mockMvc.get("/teachers") { header("X-API-Key", newKey) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `키로는 등록, 삭제나 키 재발급을 할 수 없다`() {
        val apiKey = apply("STUDENT")
        val student = studentRepository.save(Student(2315, "김준서", LocalDate.of(2009, 7, 28), "s@dsm.hs.kr"))

        listOf(
            mockMvc.post("/students") {
                header("X-API-Key", apiKey)
                contentType = MediaType.APPLICATION_JSON
                content = """{"studentNumber":2316,"name":"김","birthDate":"2009-07-28","email":"a@dsm.hs.kr"}"""
            },
            mockMvc.delete("/students/${student.id}") { header("X-API-Key", apiKey) },
            mockMvc.post("/api-keys/reissue") { header("X-API-Key", apiKey) },
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
        assertEquals(1, studentRepository.count())
        assertEquals(ApiKeySecret.hash(apiKey), apiKeyRepository.findAll().single().keyHash)
    }

    @Test
    fun `HEAD 요청도 토큰만으로는 조회할 수 없고 분야를 신청한 키로만 된다`() {
        val apiKey = apply("STUDENT")
        val student = studentRepository.save(Student(2315, "김준서", LocalDate.of(2009, 7, 28), "s@dsm.hs.kr"))

        listOf("/students", "/students/${student.id}", "/teachers", "/teachers/999").forEach { path ->
            mockMvc.perform(head(path).header("Authorization", "Bearer $adminToken"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
        mockMvc.perform(head("/students/${student.id}").header("X-API-Key", apiKey))
            .andExpect(MockMvcResultMatchers.status().isOk)
        mockMvc.perform(head("/teachers").header("X-API-Key", apiKey))
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    fun `토큰과 키를 같이 보내도 조회는 키로, 등록은 토큰으로 인증한다`() {
        val apiKey = apply("STUDENT")

        mockMvc.get("/students") {
            header("Authorization", "Bearer $adminToken")
            header("X-API-Key", apiKey)
        }.andExpect { status { isOk() } }
        mockMvc.get("/teachers") {
            header("Authorization", "Bearer $adminToken")
            header("X-API-Key", apiKey)
        }.andExpect { status { isForbidden() } }
        mockMvc.post("/students") {
            header("Authorization", "Bearer $adminToken")
            header("X-API-Key", apiKey)
            contentType = MediaType.APPLICATION_JSON
            content = """{"studentNumber":2316,"name":"김","birthDate":"2009-07-28","email":"a@dsm.hs.kr"}"""
        }.andExpect { status { isCreated() } }
    }

    // 신청 API로 분야를 신청한다. 첫 신청이면 받은 키를 돌려준다.
    private fun apply(field: String): String {
        val token = "token-$field"
        emailVerifiedTokens.tokens[token] = EMAIL
        val response = mockMvc.post("/api-keys") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"field":"$field","serviceName":"급식 알리미","purpose":"급식 공유","emailVerifiedToken":"$token"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return jsonMapper.readTree(response)["apiKey"].asString()
    }

    companion object {
        private const val EMAIL = "junseo@dsm.hs.kr"
    }
}
