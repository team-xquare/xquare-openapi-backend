package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.user.UserRepository
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.*

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAuthRepositories::class)
class EmailOtpTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val otps: FakeEmailOtpRepository,
    private val tokens: FakeEmailVerifiedTokenRepository,
    private val mail: FakeOtpMailSender,
    private val users: UserRepository,
    private val refreshTokens: FakeRefreshTokenRepository,
) {
    @AfterEach
    fun tearDown() {
        otps.clear()
        tokens.tokens.clear()
        tokens.ttls.clear()
        mail.clear()
        users.deleteAll()
        refreshTokens.clear()
    }

    @Test
    fun `발송하면 6자리 코드를 300초 저장하고 메일을 한 통 보낸다`() {
        send().andExpect {
            status { isOk() }
            jsonPath("$.message") { value("인증코드를 발송했습니다.") }
        }
        val otp = otps.otps.getValue(EMAIL)
        assertTrue(otp.toInt() in 100000..999999)
        assertEquals(Duration.ofSeconds(300), otps.ttls[EMAIL])
        assertEquals(listOf(EMAIL to otp), mail.messages)
    }

    @Test
    fun `재발송하면 이전 코드를 덮어쓴다`() {
        otps.otps[EMAIL] = "old"
        send().andExpect { status { isOk() } }
        assertEquals(mail.messages.single().second, otps.otps[EMAIL])
        assertNotEquals("old", otps.otps[EMAIL])
    }

    @Test
    fun `두 API 모두 잘못된 이메일과 빈 이메일은 INVALID_EMAIL_FORMAT`() {
        for (email in listOf("", " ", "a@gmail.com", "a@dsmXhs.kr", "a@@dsm.hs.kr", "a b@dsm.hs.kr", "a@dsm.hs.kr.other", "victim+1@dsm.hs.kr", "+@dsm.hs.kr")) {
            for (path in listOf("/auth/email/otp", "/auth/email/otp/verify")) {
                request(path, """{"email":"$email","otp":"123456"}""").andExpect {
                    status { isBadRequest() }
                    jsonPath("$.code") { value("INVALID_EMAIL_FORMAT") }
                    jsonPath("$.message") { value("@dsm.hs.kr 형식이 아닙니다.") }
                }
            }
        }
        assertTrue(otps.otps.isEmpty())
        assertTrue(mail.messages.isEmpty())
    }

    @Test
    fun `저장 실패도 500으로 변환하고 남은 코드를 정리한다`() {
        otps.failOnSave = true
        sendFailed()
        assertTrue(otps.otps.isEmpty())
        assertTrue(mail.messages.isEmpty())
    }

    @Test
    fun `메일 실패는 500이고 코드를 정리한다`() {
        mail.failOnSend = true
        sendFailed()
        assertTrue(otps.otps.isEmpty())
    }

    @Test
    fun `정리 실패로 메일 발송 오류가 가려지지 않는다`() {
        mail.failOnSend = true
        otps.failOnDelete = true
        sendFailed()
        assertNotNull(otps.otps[EMAIL])
    }

    @Test
    fun `검증 성공은 UUID 토큰을 600초 저장하고 OTP를 삭제한다`() {
        otps.otps[EMAIL] = "123456"
        val token = verifiedToken()
        assertEquals(token, UUID.fromString(token).toString())
        assertEquals(mapOf(token to EMAIL), tokens.tokens)
        assertEquals(Duration.ofSeconds(600), tokens.ttls[token])
        assertNull(otps.otps[EMAIL])
        verify("123456").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("OTP_NOT_FOUND") }
        }
        assertEquals(1, tokens.tokens.size)
    }

    @Test
    fun `코드가 없으면 형식과 관계없이 OTP_NOT_FOUND`() {
        for (otp in listOf("123456", "abc", "1234567")) {
            verify(otp).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("OTP_NOT_FOUND") }
                jsonPath("$.message") { value("발송된 인증코드가 없거나 만료되었습니다. 재발송이 필요합니다.") }
            }
        }
        assertTrue(tokens.tokens.isEmpty())
    }

    @Test
    fun `코드가 다르면 형식과 관계없이 OTP_MISMATCH이고 저장된 코드는 유지한다`() {
        otps.otps[EMAIL] = "123456"
        for (otp in listOf("654321", "abc", "1234567")) {
            verify(otp).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("OTP_MISMATCH") }
                jsonPath("$.message") { value("인증코드가 일치하지 않습니다.") }
            }
        }
        assertEquals("123456", otps.otps[EMAIL])
        assertTrue(tokens.tokens.isEmpty())
    }

    @Test
    fun `빈 OTP 누락 null 깨진 JSON은 INVALID_INPUT`() {
        for (body in listOf("""{"email":"$EMAIL","otp":""}""", """{"email":"$EMAIL","otp":" "}""", """{"email":"$EMAIL"}""", """{"email":"$EMAIL","otp":null}""", "{", "{}")) {
            request("/auth/email/otp/verify", body).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
        for (body in listOf("{", "{}", """{"email":null}""")) {
            request("/auth/email/otp", body).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
    }

    @Test
    fun `OTP 발송 검증 회원가입으로 이어진다`() {
        send().andExpect { status { isOk() } }
        val token = verifiedToken(mail.messages.single().second)
        request("/auth/signup", """{"username":"otpuser","password":"Password1!","studentNumber":2315,"name":"홍길동","email":"$EMAIL","emailVerifiedToken":"$token"}""").andExpect {
            status { isCreated() }
            jsonPath("$.accessToken") { isNotEmpty() }
            jsonPath("$.refreshToken") { isNotEmpty() }
        }
        assertEquals(EMAIL, users.findAll().single().email)
        assertNull(tokens.tokens[token])
    }

    @Test
    fun `4회 오류 뒤에는 성공하고 횟수를 지우며 5회 오류 뒤에는 코드가 폐기된다`() {
        otps.otps[EMAIL] = "123456"
        repeat(4) { mismatch() }
        verify("123456").andExpect { status { isOk() } }
        assertEquals(0, otps.attempts(EMAIL))
        otps.otps[EMAIL] = "123456"
        repeat(5) { mismatch() }
        verify("123456").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("OTP_NOT_FOUND") }
        }
    }

    @Test
    fun `재발송해도 오입력 횟수는 초기화되지 않고 이어서 센다`() {
        send().andExpect { status { isOk() } }
        repeat(3) { mismatch() }
        otps.now = at(60_000)
        send().andExpect { status { isOk() } }
        assertEquals(3, otps.attempts(EMAIL))
        repeat(2) { mismatch() }
        verify(mail.messages.last().second).andExpect { jsonPath("$.code") { value("OTP_NOT_FOUND") } }
        otps.now = at(120_000)
        send().andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "3480") }
            jsonPath("$.code") { value("OTP_ATTEMPTS_EXCEEDED") }
            jsonPath("$.message") { value("인증코드 입력 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.") }
        }
        assertEquals(2, mail.messages.size)
    }

    @Test
    fun `오입력 창이 지나면 다시 발송하고 인증할 수 있다`() {
        otps.otps[EMAIL] = "123456"
        repeat(5) { mismatch() }
        otps.now = at(3_599_999)
        send().andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "1") }
            jsonPath("$.code") { value("OTP_ATTEMPTS_EXCEEDED") }
        }
        otps.now = at(3_600_000)
        send().andExpect { status { isOk() } }
        verify(mail.messages.single().second).andExpect { status { isOk() } }
    }

    @Test
    fun `쿨다운 안의 재발송은 거부하고 코드와 메일을 그대로 둔다`() {
        send().andExpect { status { isOk() } }
        val first = otps.otps.getValue(EMAIL)
        send().andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "60") }
            jsonPath("$.code") { value("OTP_RESEND_TOO_SOON") }
            jsonPath("$.message") { value("인증코드를 너무 자주 요청했습니다. 잠시 후 다시 시도해 주세요.") }
        }
        otps.now = at(58_500)
        send().andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "2") }
            jsonPath("$.code") { value("OTP_RESEND_TOO_SOON") }
        }
        assertEquals(first, otps.otps[EMAIL])
        assertEquals(1, mail.messages.size)
        otps.now = at(60_000)
        send().andExpect { status { isOk() } }
        assertEquals(2, mail.messages.size)
        assertEquals(mail.messages.last().second, otps.otps[EMAIL])
    }

    @Test
    fun `이메일당 발송 상한을 넘으면 창이 끝날 때까지 거부한다`() {
        for (i in 0..2) {
            otps.now = at(60_000L * i)
            send(ip = "10.0.0.$i").andExpect { status { isOk() } }
        }
        otps.now = at(180_000)
        send(ip = "10.0.0.9").andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "3420") }
            jsonPath("$.code") { value("OTP_SEND_LIMIT_EXCEEDED") }
            jsonPath("$.message") { value("인증코드 발송 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.") }
        }
        send(email = "other@dsm.hs.kr", ip = "10.0.0.9").andExpect { status { isOk() } }
        assertEquals(4, mail.messages.size)
        otps.now = at(3_600_000)
        send(ip = "10.0.0.9").andExpect { status { isOk() } }
    }

    @Test
    fun `IP당 발송 상한은 이메일이 달라도 적용되고 X-Forwarded-For는 보지 않는다`() {
        for (i in 1..4) {
            send(email = "user$i@dsm.hs.kr", ip = "10.0.0.1").andExpect { status { isOk() } }
        }
        send(email = "user5@dsm.hs.kr", ip = "10.0.0.1", forwardedFor = "10.0.0.99").andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "3600") }
            jsonPath("$.code") { value("OTP_SEND_LIMIT_EXCEEDED") }
        }
        assertNull(otps.otps["user5@dsm.hs.kr"])
        send(email = "user5@dsm.hs.kr", ip = "10.0.0.2").andExpect { status { isOk() } }
        otps.now = at(3_600_000)
        send(email = "user6@dsm.hs.kr", ip = "10.0.0.1").andExpect { status { isOk() } }
    }

    @Test
    fun `IPv6는 같은 64비트 접두사를 한 IP로 보고 발송 상한을 함께 센다`() {
        for (i in 1..4) {
            send(email = "user$i@dsm.hs.kr", ip = "2001:db8:1:2::$i").andExpect { status { isOk() } }
        }
        send(email = "user5@dsm.hs.kr", ip = "2001:DB8:1:2:ffff:0:0:5").andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("OTP_SEND_LIMIT_EXCEEDED") }
        }
        send(email = "user5@dsm.hs.kr", ip = "2001:db8:1:3::5").andExpect { status { isOk() } }
        send(email = "user6@dsm.hs.kr", ip = "::ffff:10.0.0.1").andExpect { status { isOk() } }
        assertEquals(
            listOf("2001:db8:1:2:0:0:0:0/64", "2001:db8:1:3:0:0:0:0/64", "10.0.0.1"),
            otps.ips.distinct(),
        )
    }

    @Test
    fun `발송에 실패해도 쿨다운은 풀지 않는다`() {
        mail.failOnSend = true
        sendFailed().andExpect { header { doesNotExist("Retry-After") } }
        mail.failOnSend = false
        send().andExpect { jsonPath("$.code") { value("OTP_RESEND_TOO_SOON") } }
        otps.now = at(60_000)
        send().andExpect { status { isOk() } }
    }

    @Test
    fun `대소문자와 앞뒤 공백이 달라도 같은 이메일로 보고 쿨다운과 발송 상한을 함께 센다`() {
        send(email = VICTIM).andExpect { status { isOk() } }
        for (variant in listOf("Victim@dsm.hs.kr", "VICTIM@DSM.HS.KR")) {
            send(email = variant).andExpect {
                status { isTooManyRequests() }
                jsonPath("$.code") { value("OTP_RESEND_TOO_SOON") }
            }
        }
        otps.now = at(60_000)
        send(email = "Victim@dsm.hs.kr").andExpect { status { isOk() } }
        otps.now = at(120_000)
        send(email = " VICTIM@dsm.hs.kr ").andExpect { status { isOk() } }
        otps.now = at(180_000)
        send(email = "Victim@Dsm.Hs.Kr").andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("OTP_SEND_LIMIT_EXCEEDED") }
        }
        assertEquals(listOf(VICTIM, VICTIM, VICTIM), mail.messages.map { it.first })
        assertEquals(setOf(VICTIM), otps.otps.keys)
    }

    @Test
    fun `대소문자가 달라도 오입력 횟수를 함께 센다`() {
        send(email = "Victim@dsm.hs.kr").andExpect { status { isOk() } }
        val otp = mail.messages.single().second
        for (variant in listOf(VICTIM, "Victim@dsm.hs.kr", "VICTIM@dsm.hs.kr", "victim@DSM.HS.KR", "vIcTiM@dsm.hs.kr")) {
            verify("wrong", variant).andExpect { jsonPath("$.code") { value("OTP_MISMATCH") } }
        }
        assertEquals(5, otps.attempts(VICTIM))
        verify(otp, VICTIM).andExpect { jsonPath("$.code") { value("OTP_NOT_FOUND") } }
        otps.now = at(60_000)
        send(email = "VICTIM@dsm.hs.kr").andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("OTP_ATTEMPTS_EXCEEDED") }
        }
    }

    @Test
    fun `대문자로 받은 인증 토큰으로 소문자 이메일은 가입되고 다른 이메일은 가입되지 않는다`() {
        send(email = "Victim@DSM.HS.KR").andExpect { status { isOk() } }
        val token = verifiedToken(mail.messages.single().second, email = "VICTIM@dsm.hs.kr")
        assertEquals(VICTIM, tokens.tokens[token])
        signup("victim+1@dsm.hs.kr", token).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
        signup("other@dsm.hs.kr", token).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }
        signup(VICTIM, token).andExpect { status { isCreated() } }
        assertEquals(VICTIM, users.findAll().single().email)
    }

    private fun sendFailed() = send().andExpect {
        status { isInternalServerError() }
        jsonPath("$.code") { value("EMAIL_SEND_FAILED") }
        jsonPath("$.message") { value("메일 발송 또는 인증코드 저장에 실패했습니다.") }
    }
    private fun verifiedToken(otp: String = "123456", email: String = EMAIL): String {
        val response = verify(otp, email).andExpect { status { isOk() } }.andReturn().response.contentAsString
        return JsonPath.read(response, "$.emailVerifiedToken")
    }
    private fun mismatch() = verify("wrong").andExpect { jsonPath("$.code") { value("OTP_MISMATCH") } }
    private fun at(millis: Long) = Instant.EPOCH.plusMillis(millis)
    private fun send(email: String = EMAIL, ip: String = "127.0.0.1", forwardedFor: String? = null) =
        mockMvc.post("/auth/email/otp") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email"}"""
            with { it.apply { remoteAddr = ip } }
            if (forwardedFor != null) header("X-Forwarded-For", forwardedFor)
        }
    private fun verify(otp: String, email: String = EMAIL) = request("/auth/email/otp/verify", """{"email":"$email","otp":"$otp"}""")
    private fun signup(email: String, token: String) = request(
        "/auth/signup",
        """{"username":"otpuser","password":"Password1!","studentNumber":2315,"name":"홍길동","email":"$email","emailVerifiedToken":"$token"}""",
    )
    private fun request(path: String, body: String) = mockMvc.post(path) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }
    companion object {
        private const val EMAIL = "example@dsm.hs.kr"
        private const val VICTIM = "victim@dsm.hs.kr"
    }
}
