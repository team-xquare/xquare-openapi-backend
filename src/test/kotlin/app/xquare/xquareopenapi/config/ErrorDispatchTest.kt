package app.xquare.xquareopenapi.config

import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

// MockMvc는 /error 포워드(ERROR 디스패치)를 흉내 내지 않으므로 실제 서버를 띄워 확인한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchTest @Autowired constructor(
    private val jwtProvider: JwtProvider,
) {

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `처리하지 못한 오류는 401로 가리지 않고 원래 상태 코드로 응답한다`() {
        val response = send(
            HttpRequest.newBuilder(uri("/auth/signup"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("hello")),
        )

        assertEquals(415, response.statusCode())
    }

    @Test
    fun `인증이 필요한 경로는 여전히 토큰 없이 접근할 수 없다`() {
        val response = send(HttpRequest.newBuilder(uri("/students")).GET())

        assertEquals(401, response.statusCode())
    }

    @Test
    fun `권한이 부족하면 403 FORBIDDEN을 JSON으로 응답한다`() {
        val response = send(
            HttpRequest.newBuilder(uri("/students"))
                .header("Authorization", "Bearer ${jwtProvider.generateAccessToken(1L, Role.USER)}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")),
        )

        assertEquals(403, response.statusCode())
        assertEquals("""{"code":"FORBIDDEN","message":"권한이 없습니다."}""", response.body())
    }

    private fun uri(path: String) = URI("http://localhost:$port$path")

    private fun send(request: HttpRequest.Builder): HttpResponse<String> =
        HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString())
}
