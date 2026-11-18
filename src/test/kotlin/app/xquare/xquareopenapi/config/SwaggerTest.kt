package app.xquare.xquareopenapi.config

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
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
            jsonPath("$.paths['/auth/signup']") { exists() }
        }
    }

    @Test
    fun `API 문서에 Bearer 토큰 인증 방식이 등록되어 기본으로 적용된다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
            jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
            jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat") { value("JWT") }
            jsonPath("$.security[0].bearerAuth") { exists() }
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
            listOf("/students", "/students/{studentId}", "/teachers", "/teachers/{teacherId}").forEach { path ->
                jsonPath("$.paths['$path'].get.security.length()") { value(1) }
                jsonPath("$.paths['$path'].get.security[0].apiKeyAuth") { exists() }
            }
            jsonPath("$.paths['/students'].post.security") { doesNotExist() }
            jsonPath("$.paths['/students/{studentId}'].patch.security") { doesNotExist() }
            jsonPath("$.paths['/teachers/{teacherId}'].delete.security") { doesNotExist() }
            jsonPath("$.paths['/api-keys'].post.security") { doesNotExist() }
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
}
