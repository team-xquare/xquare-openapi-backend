package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.auth.mail.ResendOtpMailSender
import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailOtpSendRequest
import app.xquare.xquareopenapi.domain.auth.usecase.SendEmailOtpUseCase
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.mail.ResendProperties
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.thymeleaf.TemplateEngine
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.InetSocketAddress
import kotlin.test.*

class ResendOtpMailSenderTest {
    private fun engine(): TemplateEngine {
        val resolver = ClassLoaderTemplateResolver()
        resolver.prefix = "templates/"
        resolver.suffix = ".html"
        resolver.characterEncoding = "UTF-8"
        val engine = SpringTemplateEngine()
        engine.setTemplateResolver(resolver)
        return engine
    }

    @Test
    fun `템플릿은 실제 OTP와 유효시간을 HTML로 렌더링한다`() {
        val context = Context()
        context.setVariable("otp", "987654")
        val html = engine().process("email/otp", context)
        assertTrue(html.contains("<strong>987654</strong>"))
        assertTrue(html.contains("5분"))
        assertFalse(html.contains("123456"))
    }

    @Test
    fun `Resend에 인증 헤더와 수신인 발신인 제목 HTML을 전송한다`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var path = ""
        var method = ""
        var authorization = ""
        var contentType = ""
        var body = ""
        server.createContext("/") { exchange ->
            path = exchange.requestURI.path
            method = exchange.requestMethod
            authorization = exchange.requestHeaders.getFirst("Authorization") ?: ""
            contentType = exchange.requestHeaders.getFirst("Content-Type") ?: ""
            body = exchange.requestBody.bufferedReader().readText()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write("""{"id":"mail-id"}""".toByteArray()) }
        }
        server.start()
        try {
            val sender = ResendOtpMailSender(ResendProperties("secret", "XQUARE <noreply@dsm.hs.kr>", "http://127.0.0.1:${server.address.port}"), engine())
            sender.send("example@dsm.hs.kr", "987654")
            assertEquals("/emails", path)
            assertEquals("POST", method)
            assertEquals("Bearer secret", authorization)
            assertEquals("application/json", contentType)
            val json = jacksonObjectMapper().readTree(body)
            assertEquals("XQUARE <noreply@dsm.hs.kr>", json["from"].stringValue())
            assertEquals("example@dsm.hs.kr", json["to"][0].stringValue())
            assertEquals(1, json["to"].size())
            assertEquals("[XQUARE] 이메일 인증코드", json["subject"].stringValue())
            assertTrue(json["html"].stringValue().contains("<strong>987654</strong>"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `Resend 4xx와 5xx는 발송 실패로 변환하고 OTP를 정리한다`() {
        for (status in listOf(401, 429, 500)) {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
            server.start()
            try {
                fails("secret", "http://127.0.0.1:${server.address.port}")
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun `빈 API 키와 연결 실패도 발송 실패로 변환한다`() {
        fails("", "http://127.0.0.1:1")
        fails(" ", "http://127.0.0.1:1")
        fails("secret", "http://127.0.0.1:1")
    }

    @Test
    fun `빈 API 키는 HTTP 요청 전에 거부한다`() {
        for (key in listOf("", " ")) {
            val sender = ResendOtpMailSender(ResendProperties(key, "noreply@dsm.hs.kr", "http://127.0.0.1:1"), engine())
            val error = assertFailsWith<IllegalStateException> {
                sender.send("example@dsm.hs.kr", "123456")
            }
            assertEquals("Resend API 키가 설정되지 않았습니다.", error.message)
        }
    }

    @Test
    fun `응답 없는 서버는 읽기 제한 시간 안에 발송 실패로 끝난다`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val release = java.util.concurrent.CountDownLatch(1)
        server.createContext("/") { exchange ->
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        try {
            val repository = FakeEmailOtpRepository()
            val sender = ResendOtpMailSender(
                ResendProperties("secret", "noreply@dsm.hs.kr", "http://127.0.0.1:${server.address.port}",
                    connectTimeout = java.time.Duration.ofMillis(200),
                    readTimeout = java.time.Duration.ofMillis(100)),
                engine(),
            )
            org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(2)) {
                val error = assertFailsWith<XquareException> {
                    SendEmailOtpUseCase(repository, sender, java.security.SecureRandom())
                        .execute(EmailOtpSendRequest("example@dsm.hs.kr"))
                }
                assertEquals(ErrorCode.EMAIL_SEND_FAILED, error.errorCode)
                assertTrue(repository.otps.isEmpty())
            }
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    @Test
    fun `연결과 읽기 타임아웃을 HTTP 연결에 적용한다`() {
        val defaults = ResendProperties("secret", "noreply@dsm.hs.kr", "http://127.0.0.1")
        assertEquals(java.time.Duration.ofSeconds(5), defaults.connectTimeout)
        assertEquals(java.time.Duration.ofSeconds(10), defaults.readTimeout)
        val properties = defaults.copy(connectTimeout = java.time.Duration.ofMillis(123),
            readTimeout = java.time.Duration.ofMillis(456))
        val sender = ResendOtpMailSender(properties, engine())
        val factory = org.springframework.test.util.ReflectionTestUtils.getField(sender, "requestFactory")
            as org.springframework.http.client.SimpleClientHttpRequestFactory
        val request = factory.createRequest(java.net.URI("http://127.0.0.1"), org.springframework.http.HttpMethod.POST)
        val connection = org.springframework.test.util.ReflectionTestUtils.getField(request, "connection")
            as java.net.HttpURLConnection
        assertEquals(123, connection.connectTimeout)
        assertEquals(456, connection.readTimeout)
    }

    private fun fails(key: String, url: String) {
        val repository = FakeEmailOtpRepository()
        val sender = ResendOtpMailSender(ResendProperties(key, "noreply@dsm.hs.kr", url), engine())
        val error = assertFailsWith<XquareException> {
            SendEmailOtpUseCase(repository, sender, java.security.SecureRandom()).execute(EmailOtpSendRequest("example@dsm.hs.kr"))
        }
        assertEquals(ErrorCode.EMAIL_SEND_FAILED, error.errorCode)
        assertTrue(repository.otps.isEmpty())
    }
}
