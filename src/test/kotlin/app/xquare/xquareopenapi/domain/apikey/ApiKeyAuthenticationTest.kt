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
class ApiKeyAuthenticationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val apiKeyRepository: ApiKeyRepository,
    private val studentRepository: StudentRepository,
    private val jsonMapper: JsonMapper,
    jwtProvider: JwtProvider,
) {
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)
    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)

    @AfterEach
    fun tearDown() {
        apiKeyRepository.deleteAll()
        studentRepository.deleteAll()
    }

    @Test
    fun `발급받은 키로 학생과 교직원을 조회할 수 있다`() {
        val apiKey = issue()

        listOf("/students", "/teachers").forEach { path ->
            mockMvc.get(path) { header("X-API-Key", apiKey) }.andExpect { status { isOk() } }
        }
    }

    @Test
    fun `재발급하면 이전 키는 바로 못 쓰고 새 키만 쓸 수 있다`() {
        val oldKey = issue()

        val newKey = jsonMapper.readTree(
            mockMvc.post("/api-keys/reissue") { header("Authorization", "Bearer $userToken") }
                .andReturn().response.contentAsString,
        )["apiKey"].asString()

        mockMvc.get("/students") { header("X-API-Key", oldKey) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_API_KEY") }
        }
        mockMvc.get("/students") { header("X-API-Key", newKey) }.andExpect { status { isOk() } }
    }

    @Test
    fun `키로는 등록, 삭제나 키 재발급을 할 수 없다`() {
        val apiKey = issue()
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
    fun `HEAD 요청도 토큰만으로는 조회할 수 없고 키로만 된다`() {
        val apiKey = issue()
        val student = studentRepository.save(Student(2315, "김준서", LocalDate.of(2009, 7, 28), "s@dsm.hs.kr"))

        listOf("/students", "/students/${student.id}", "/teachers", "/teachers/999").forEach { path ->
            mockMvc.perform(head(path).header("Authorization", "Bearer $adminToken"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
        mockMvc.perform(head("/students/${student.id}").header("X-API-Key", apiKey))
            .andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test
    fun `토큰과 키를 같이 보내도 조회는 키로, 등록은 토큰으로 인증한다`() {
        val apiKey = issue()

        mockMvc.get("/students") {
            header("Authorization", "Bearer $adminToken")
            header("X-API-Key", apiKey)
        }.andExpect { status { isOk() } }
        mockMvc.post("/students") {
            header("Authorization", "Bearer $adminToken")
            header("X-API-Key", apiKey)
            contentType = MediaType.APPLICATION_JSON
            content = """{"studentNumber":2316,"name":"김","birthDate":"2009-07-28","email":"a@dsm.hs.kr"}"""
        }.andExpect { status { isCreated() } }
    }

    private fun issue(): String =
        jsonMapper.readTree(
            mockMvc.post("/api-keys") {
                header("Authorization", "Bearer $userToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"serviceName":"급식 알리미","purpose":"급식 공유"}"""
            }.andReturn().response.contentAsString,
        )["apiKey"].asString()
}
