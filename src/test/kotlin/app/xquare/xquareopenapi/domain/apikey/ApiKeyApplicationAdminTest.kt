package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyApplicationAdminTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
    private val userRepository: UserRepository,
    jwtProvider: JwtProvider,
) {
    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    private var junseoId = 0L
    private var minjiId = 0L
    private lateinit var junseoKey: ApiKey
    private lateinit var minjiKey: ApiKey

    // 준서: STUDENT(FIRST, 1일) → TEACHER(ADD_FIELD, 3일), 민지: CLUB(FIRST, 2일)
    @BeforeEach
    fun setUp() {
        junseoId = userRepository.saveUser("junseo").id!!
        minjiId = userRepository.saveUser("minji").id!!
        junseoKey = apiKeyRepository.save(ApiKey(junseoId, ApiKeySecret.hash("xq_junseo")))
        minjiKey = apiKeyRepository.save(ApiKey(minjiId, ApiKeySecret.hash("xq_minji")))
        save(junseoKey, ApiKeyField.STUDENT, ApiKeyApplicationType.FIRST, day = 1)
        save(minjiKey, ApiKeyField.CLUB, ApiKeyApplicationType.FIRST, day = 2)
        save(junseoKey, ApiKeyField.TEACHER, ApiKeyApplicationType.ADD_FIELD, day = 3)
    }

    @AfterEach
    fun tearDown() {
        apiKeyApplicationRepository.deleteAll()
        apiKeyRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `목록은 최근 신청부터 신청자 userId와 함께 페이지로 보여 준다`() {
        list("").andExpect {
            status { isOk() }
            jsonPath("$.content[*].field") { value(contains("TEACHER", "CLUB", "STUDENT")) }
            jsonPath("$.content[0].userId") { value(junseoId) }
            jsonPath("$.content[0].type") { value("ADD_FIELD") }
            jsonPath("$.content[0].serviceName") { value("TEACHER 서비스") }
            jsonPath("$.content[0].purpose") { value("TEACHER 목적") }
            jsonPath("$.content[0].appliedAt") { value("2026-11-03T09:00:00") }
            jsonPath("$.content[1].userId") { value(minjiId) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(20) }
            jsonPath("$.totalCount") { value(3) }
            jsonPath("$.totalPage") { value(1) }
        }
    }

    @Test
    fun `신청 시각이 같으면 나중에 들어온 신청이 먼저다`() {
        val same = save(minjiKey, ApiKeyField.SCHEDULE, ApiKeyApplicationType.ADD_FIELD, day = 3)

        list("").andExpect {
            jsonPath("$.content[0].id") { value(same.id) }
            jsonPath("$.content[1].field") { value("TEACHER") }
        }
    }

    @Test
    fun `분야와 구분으로 거를 수 있고 둘 다 주면 AND로 묶는다`() {
        list("?field=STUDENT").andExpect {
            jsonPath("$.content[*].field") { value(contains("STUDENT")) }
        }
        list("?type=FIRST").andExpect {
            jsonPath("$.content[*].field") { value(contains("CLUB", "STUDENT")) }
        }
        list("?type=ADD_FIELD").andExpect {
            jsonPath("$.content[*].field") { value(contains("TEACHER")) }
        }
        list("?field=TEACHER&type=FIRST").andExpect {
            jsonPath("$.totalCount") { value(0) }
        }
    }

    @Test
    fun `page와 size로 나눠 본다`() {
        list("?page=1&size=2").andExpect {
            jsonPath("$.content[*].field") { value(contains("STUDENT")) }
            jsonPath("$.page") { value(1) }
            jsonPath("$.size") { value(2) }
            jsonPath("$.totalCount") { value(3) }
            jsonPath("$.totalPage") { value(2) }
        }
    }

    @Test
    fun `잘못된 조건은 INVALID_INPUT`() {
        listOf(
            "?field=MEAL" to "field: 형식이 올바르지 않습니다.",
            "?type=SECOND" to "type: 형식이 올바르지 않습니다.",
            "?page=-1" to "page",
            "?page=21474837" to "page",
            "?size=0" to "size",
            "?size=101" to "size",
        ).forEach { (query, message) ->
            list(query).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(message)) }
            }
        }
    }

    @Test
    fun `page와 size는 경계값까지 받는다`() {
        list("?page=21474836&size=100").andExpect { status { isOk() } }
        list("?size=1").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(1) }
        }
    }

    @Test
    fun `상세는 신청 내용과 신청자, 그 키가 신청한 분야 전체를 보여 준다`() {
        val first = apiKeyApplicationRepository.findAll().single { it.field == ApiKeyField.STUDENT }

        detail(first.id!!).andExpect {
            status { isOk() }
            jsonPath("$.id") { value(first.id) }
            jsonPath("$.field") { value("STUDENT") }
            jsonPath("$.type") { value("FIRST") }
            jsonPath("$.serviceName") { value("STUDENT 서비스") }
            jsonPath("$.purpose") { value("STUDENT 목적") }
            jsonPath("$.appliedAt") { value("2026-11-01T09:00:00") }
            jsonPath("$.userId") { value(junseoId) }
            jsonPath("$.applicant.username") { value("junseo") }
            jsonPath("$.applicant.name") { value("김준서") }
            jsonPath("$.applicant.email") { value("junseo@dsm.hs.kr") }
            jsonPath("$.applicant.studentNumber") { value(2315) }
            jsonPath("$.fields[*].field") { value(contains("STUDENT", "TEACHER")) }
            jsonPath("$.fields[1].appliedAt") { value("2026-11-03T09:00:00") }
        }
    }

    @Test
    fun `신청자 행이 없으면 applicant는 null이다`() {
        val club = apiKeyApplicationRepository.findAll().single { it.field == ApiKeyField.CLUB }
        userRepository.deleteById(minjiId)

        detail(club.id!!).andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(minjiId) }
            jsonPath("$.applicant") { value(nullValue()) }
            jsonPath("$.fields[*].field") { value(contains("CLUB")) }
        }
    }

    @Test
    fun `없는 신청이면 API_KEY_APPLICATION_NOT_FOUND, 숫자가 아니면 INVALID_INPUT`() {
        detail(999_999).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("API_KEY_APPLICATION_NOT_FOUND") }
            jsonPath("$.message") { value("존재하지 않는 활용신청입니다.") }
        }
        mockMvc.get("/api-key-applications/abc") { header("Authorization", "Bearer $adminToken") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    @Test
    fun `ADMIN이 아니면 FORBIDDEN, 토큰이 없거나 키로 요청하면 INVALID_TOKEN`() {
        val id = apiKeyApplicationRepository.findAll().first().id
        listOf("/api-key-applications", "/api-key-applications/$id").forEach { path ->
            mockMvc.get(path) { header("Authorization", "Bearer $userToken") }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
            mockMvc.perform(head(path).header("Authorization", "Bearer $userToken"))
                .andExpect(MockMvcResultMatchers.status().isForbidden)
            mockMvc.get(path).andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
            mockMvc.get(path) { header("X-API-Key", "xq_junseo") }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
    }

    private fun save(apiKey: ApiKey, field: ApiKeyField, type: ApiKeyApplicationType, day: Int) =
        apiKeyApplicationRepository.save(
            ApiKeyApplication(apiKey.id!!, field, type, "${field.name} 서비스", "${field.name} 목적", LocalDateTime.of(2026, 11, day, 9, 0)),
        )

    private fun list(query: String) =
        mockMvc.get("/api-key-applications$query") { header("Authorization", "Bearer $adminToken") }

    private fun detail(id: Long) =
        mockMvc.get("/api-key-applications/$id") { header("Authorization", "Bearer $adminToken") }
}
