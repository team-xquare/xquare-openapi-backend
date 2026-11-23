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
            jsonPath("$.paths['/students'].get") { exists() }
            jsonPath("$.paths['/students'].get.security") { doesNotHaveJsonPath() }
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
}
