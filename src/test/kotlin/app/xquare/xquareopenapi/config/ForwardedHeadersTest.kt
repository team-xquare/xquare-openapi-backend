package app.xquare.xquareopenapi.config

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import app.xquare.xquareopenapi.domain.auth.FakeEmailOtpRepository
import app.xquare.xquareopenapi.domain.auth.FakeOtpMailSender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

// MockMvc는 Tomcat RemoteIpValve를 거치지 않으므로 실제 서버를 띄워 OTP 발송 IP 키가 어떻게 정해지는지 확인한다.
// 테스트 클라이언트는 루프백(127.0.0.1)으로 붙는데, 루프백은 internal-proxies 기본값에 들어 있어 프록시로 취급된다.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=jdbc:h2:mem:forwarded-headers;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
@Import(FakeAuthRepositories::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ForwardedHeadersTest @Autowired constructor(
    private val otps: FakeEmailOtpRepository,
    private val mail: FakeOtpMailSender,
) {
    @LocalServerPort
    private var port: Int = 0

    @AfterEach
    fun tearDown() {
        otps.clear()
        mail.clear()
    }

    @Test
    fun `내부망 프록시가 보낸 X-Forwarded-For의 클라이언트 IP로 발송 횟수를 센다`() {
        assertEquals(200, sendOtp(port, "user1@dsm.hs.kr", "203.0.113.7").statusCode())
        // 클라이언트가 앞에 끼워 넣은 값은 무시하고, 신뢰하는 프록시 바로 앞의 IP를 쓴다.
        assertEquals(200, sendOtp(port, "user2@dsm.hs.kr", "198.51.100.1, 203.0.113.8").statusCode())
        assertEquals(200, sendOtp(port, "user3@dsm.hs.kr", null).statusCode())
        assertEquals(listOf("203.0.113.7", "203.0.113.8", "127.0.0.1"), otps.ips)
    }
}

// internal-proxies에서 루프백을 빼면 루프백 연결은 신뢰하지 않는 직접 연결이 된다.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:untrusted-forwarded-headers;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "server.tomcat.remoteip.internal-proxies=192.0.2.0/24",
    ],
)
@Import(FakeAuthRepositories::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UntrustedForwardedHeadersTest @Autowired constructor(
    private val otps: FakeEmailOtpRepository,
    private val mail: FakeOtpMailSender,
) {
    @LocalServerPort
    private var port: Int = 0

    @AfterEach
    fun tearDown() {
        otps.clear()
        mail.clear()
    }

    @Test
    fun `신뢰하지 않는 직접 연결의 X-Forwarded-For는 무시하고 연결한 IP로 센다`() {
        assertEquals(200, sendOtp(port, "user1@dsm.hs.kr", "203.0.113.7").statusCode())
        assertEquals(listOf("127.0.0.1"), otps.ips)
    }
}

private fun sendOtp(port: Int, email: String, forwardedFor: String?): HttpResponse<String> {
    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/auth/email/otp"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email"}"""))
    if (forwardedFor != null) request.header("X-Forwarded-For", forwardedFor)
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString())
}
