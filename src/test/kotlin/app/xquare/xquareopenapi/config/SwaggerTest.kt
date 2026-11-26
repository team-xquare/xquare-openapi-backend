package app.xquare.xquareopenapi.config

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `API 문서는 토큰 없이 받을 수 있다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.openapi") { exists() }
            jsonPath("$.info.title") { value("XQUARE Open API") }
            jsonPath("$.info.version") { value("0.1.0") }
            jsonPath("$.paths['/auth/signup']") { exists() }
            jsonPath("$.paths['/auth/password/reset'].post") { exists() }
        }
    }

    @Test
    fun `API 문서의 기본 인증 방식은 Bearer JWT다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
            jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
            jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat") { value("JWT") }
            jsonPath("$.security[0].bearerAuth") { exists() }
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
        "post, /auth/signup",
        "post, /auth/login",
        "post, /auth/reissue",
        "post, /auth/email/otp",
        "post, /auth/email/otp/verify",
        "post, /auth/password/reset",
        "get, /meals",
        "get, /meals/{mealId}",
        "get, /timetables",
    )
    fun `공개 API는 기본 인증을 빈 배열로 재정의한다`(method: String, path: String) {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            val security = "\$.paths['$path'].$method.security"
            jsonPath(security) { isArray() }
            jsonPath("$security.length()") { value(0) }
        }
    }

    @Test
    fun `보호된 API는 문서의 기본 인증 방식을 상속한다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/meals'].post") { exists() }
            jsonPath("$.paths['/meals'].post.security") { doesNotHaveJsonPath() }
            jsonPath("$.paths['/meals/{mealId}'].patch") { exists() }
            jsonPath("$.paths['/meals/{mealId}'].patch.security") { doesNotHaveJsonPath() }
            // 학생·교직원 조회 GET은 API 키 인증으로 재정의되므로 여기서 보지 않는다(아래 API 키 테스트가 확인).
            jsonPath("$.paths['/students'].post") { exists() }
            jsonPath("$.paths['/students'].post.security") { doesNotHaveJsonPath() }
        }
    }

    @Test
    fun `API 문서에 X-API-Key 헤더 인증 방식이 등록된다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.components.securitySchemes.apiKeyAuth.type") { value("apiKey") }
            jsonPath("$.components.securitySchemes.apiKeyAuth.in") { value("header") }
            jsonPath("$.components.securitySchemes.apiKeyAuth.name") { value("X-API-Key") }
        }
    }

    @Test
    fun `조회 API의 GET에만 API 키 인증이 걸리고 같은 경로의 다른 메서드는 Bearer 토큰 그대로다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            listOf(
                "/students", "/students/{studentId}", "/teachers", "/teachers/{teacherId}",
                "/clubs", "/clubs/{clubId}", "/study-supervisors", "/study-supervisors/day",
                "/schedules", "/schedules/{scheduleId}",
            ).forEach { path ->
                jsonPath("$.paths['$path'].get.security.length()") { value(1) }
                jsonPath("$.paths['$path'].get.security[0].apiKeyAuth") { exists() }
            }
            jsonPath("$.paths['/students'].post.security") { doesNotExist() }
            jsonPath("$.paths['/students/{studentId}'].patch.security") { doesNotExist() }
            jsonPath("$.paths['/teachers/{teacherId}'].delete.security") { doesNotExist() }
            jsonPath("$.paths['/clubs'].post.security") { doesNotExist() }
            jsonPath("$.paths['/schedules/{scheduleId}'].patch.security") { doesNotExist() }
            jsonPath("$.paths['/study-supervisors/{studySupervisorId}'].delete.security") { doesNotExist() }
            // 프로젝트(키 관리) API는 액세스 토큰으로 연다.
            jsonPath("$.paths['/projects'].post") { exists() }
            jsonPath("$.paths['/projects'].post.security") { doesNotExist() }
            jsonPath("$.paths['/projects/{projectId}/fields'].post.security") { doesNotExist() }
            jsonPath("$.paths['/projects'].get") { exists() }
            jsonPath("$.paths['/projects'].get.security") { doesNotExist() }
            jsonPath("$.paths['/projects/{projectId}'].get.security") { doesNotExist() }
            jsonPath("$.paths['/admin/api-applications'].get") { exists() }
            jsonPath("$.paths['/admin/api-applications'].get.security") { doesNotExist() }
            jsonPath("$.paths['/admin/api-applications/{applicationId}'].get.security") { doesNotExist() }
        }
    }

    @Test
    fun `내 정보 조회는 토큰의 사용자 id를 쓰므로 문서에 요청 파라미터가 없다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.paths['/users/me'].get") { exists() }
            jsonPath("$.paths['/users/me'].get.parameters") { doesNotExist() }
        }
    }

    @Test
    fun `Swagger UI 화면은 토큰 없이 열린다`() {
        mockMvc.get("/swagger-ui/index.html").andExpect {
            status { isOk() }
            content { string(containsString("Swagger UI")) }
        }
    }

    @Test
    fun `Swagger 경로를 열어도 다른 API는 여전히 토큰이 필요하다`() {
        mockMvc.get("/students").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `조회 API의 query DTO는 request 하나로 뭉개지지 않고 개별 query 파라미터로 노출된다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            mapOf(
                "/students" to listOf("name", "grade", "classNum", "page", "size"),
                "/teachers" to listOf("name", "grade", "classNum", "subject", "office", "page", "size"),
                "/clubs" to listOf("name", "member", "room", "teacher", "page", "size"),
                "/meals" to listOf("startDate", "finishDate"),
                "/notices" to listOf("page", "size"),
                "/study-supervisors" to listOf("startDate", "finishDate"),
                "/study-supervisors/day" to listOf("date"),
                "/timetables" to listOf("grade", "classNum", "date"),
                "/schedules" to listOf("startDate", "finishDate"),
                "/admin/api-applications" to listOf("status", "page", "size"),
                "/admin/api-usage-logs" to listOf("from", "to", "method", "status", "page", "size", "api", "field", "level", "projectId", "userId", "errorCode"),
            ).forEach { (path, expectedParams) ->
                jsonPath("$.paths['$path'].get.parameters.length()") { value(expectedParams.size) }
                expectedParams.forEachIndexed { index, name ->
                    jsonPath("$.paths['$path'].get.parameters[$index].name") { value(name) }
                    jsonPath("$.paths['$path'].get.parameters[$index].in") { value("query") }
                }
            }
        }
    }

    @Test
    fun `리소스 생성 POST API는 201 응답과 Location 헤더를 문서화한다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            listOf(
                "/students",
                "/teachers",
                "/schedules",
                "/study-supervisors",
                "/meals",
                "/clubs",
                "/notices",
            ).forEach { path ->
                jsonPath("$.paths['$path'].post.responses['201']") { exists() }
                jsonPath("$.paths['$path'].post.responses['201'].headers.Location") { exists() }
                jsonPath("$.paths['$path'].post.responses['200']") { doesNotExist() }
            }
        }
    }

    @Test
    fun `교직원 및 자습 감독 DTO 스키마가 실제 JSON 직렬화와 일치한다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.components.schemas.TeacherRequest.properties.isManager") { exists() }
            jsonPath("$.components.schemas.TeacherRequest.properties.manager") { doesNotExist() }
            jsonPath("$.components.schemas.TeacherRequest.properties.homeroomComplete") { doesNotExist() }
            jsonPath("$.components.schemas.TeacherResponse.properties.isManager") { exists() }
            jsonPath("$.components.schemas.TeacherResponse.properties.manager") { doesNotExist() }
            jsonPath("$.components.schemas.StudySupervisorRequest.properties.floorsDistinct") { doesNotExist() }
            jsonPath("$.components.schemas.ErrorResponse") { exists() }
        }
    }

    @Test
    fun `프로젝트 및 관리자 활용신청 API가 API 키 태그와 권한 확장으로 문서화된다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/projects'].post.tags[0]") { value("API 키") }
            jsonPath("$.paths['/projects'].post.responses['201']") { exists() }
            jsonPath("$.paths['/projects/{projectId}/fields'].post.responses['201']") { exists() }
            jsonPath("$.paths['/projects/{projectId}/api-key/renew'].post.tags[0]") { value("API 키") }
            jsonPath("$.paths['/admin/api-applications'].get.tags[0]") { value("API 키") }
            jsonPath("$.paths['/admin/api-applications/{applicationId}/approve'].post.x-required-role") { value("ADMIN") }
        }
    }
}
