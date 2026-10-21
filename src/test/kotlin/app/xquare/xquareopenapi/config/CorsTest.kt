package app.xquare.xquareopenapi.config

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.containsStringIgnoringCase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options
import org.springframework.test.web.servlet.post

// 허용 origin은 src/test/resources/application.yaml의 cors.allowed-origins(http://localhost:3000)다.
@SpringBootTest
@AutoConfigureMockMvc
class CorsTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `허용한 origin의 preflight에는 CORS 헤더로 응답한다`() {
        mockMvc.options("/auth/signup") {
            header("Origin", ALLOWED_ORIGIN)
            header("Access-Control-Request-Method", "POST")
            header("Access-Control-Request-Headers", "Authorization, Content-Type")
        }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Origin", ALLOWED_ORIGIN) }
            header { string("Access-Control-Allow-Methods", containsString("POST")) }
            header { string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")) }
            header { string("Access-Control-Max-Age", "3600") }
        }
    }

    @Test
    fun `허용하지 않은 origin의 preflight는 거절한다`() {
        mockMvc.options("/auth/signup") {
            header("Origin", "http://evil.com")
            header("Access-Control-Request-Method", "POST")
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist("Access-Control-Allow-Origin") }
        }
    }

    @Test
    fun `인증이 필요한 경로도 preflight는 토큰 없이 통과한다`() {
        mockMvc.options("/students") {
            header("Origin", ALLOWED_ORIGIN)
            header("Access-Control-Request-Method", "GET")
            header("Access-Control-Request-Headers", "Authorization")
        }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Origin", ALLOWED_ORIGIN) }
        }
    }

    @Test
    fun `실제 요청 응답에도 허용 origin 헤더를 붙인다`() {
        mockMvc.post("/auth/signup") {
            header("Origin", ALLOWED_ORIGIN)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isBadRequest() }
            header { string("Access-Control-Allow-Origin", ALLOWED_ORIGIN) }
        }
    }

    @Test
    fun `실제 요청 응답에 Location 헤더를 읽을 수 있다고 알린다`() {
        mockMvc.post("/auth/signup") {
            header("Origin", ALLOWED_ORIGIN)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            header { string("Access-Control-Expose-Headers", "Location") }
        }
    }

    companion object {
        private const val ALLOWED_ORIGIN = "http://localhost:3000"
    }
}
