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
        for (email in listOf("", " ", "a@gmail.com", "a@dsmXhs.kr", "a@@dsm.hs.kr", "a b@dsm.hs.kr", "a@dsm.hs.kr.other")) {
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
    fun `4회 오류 뒤에는 성공하고 5회 오류 뒤에는 재발송이 필요하다`() {
        otps.save(EMAIL, "123456", Duration.ofSeconds(300))
        repeat(4) { verify("wrong").andExpect { jsonPath("$.code") { value("OTP_MISMATCH") } } }
        verify("123456").andExpect { status { isOk() } }
        otps.save(EMAIL, "123456", Duration.ofSeconds(300))
        repeat(5) { verify("wrong").andExpect { jsonPath("$.code") { value("OTP_MISMATCH") } } }
        verify("123456").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("OTP_NOT_FOUND") }
        }
        send().andExpect { status { isOk() } }
        repeat(4) { verify("wrong").andExpect { jsonPath("$.code") { value("OTP_MISMATCH") } } }
        verify(mail.messages.last().second).andExpect { status { isOk() } }
    }

    private fun sendFailed() = send().andExpect {
        status { isInternalServerError() }
        jsonPath("$.code") { value("EMAIL_SEND_FAILED") }
        jsonPath("$.message") { value("메일 발송 또는 인증코드 저장에 실패했습니다.") }
    }
    private fun verifiedToken(otp: String = "123456"): String {
        val response = verify(otp).andExpect { status { isOk() } }.andReturn().response.contentAsString
        return JsonPath.read(response, "$.emailVerifiedToken")
    }
    private fun send() = request("/auth/email/otp", """{"email":"$EMAIL"}""")
    private fun verify(otp: String) = request("/auth/email/otp/verify", """{"email":"$EMAIL","otp":"$otp"}""")
    private fun request(path: String, body: String) = mockMvc.post(path) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }
    companion object {
        private const val EMAIL = "example@dsm.hs.kr"
    }
}
