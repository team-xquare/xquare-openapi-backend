package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailOtpSendRequest
import app.xquare.xquareopenapi.domain.auth.usecase.SendEmailOtpUseCase
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.security.SecureRandom
import kotlin.test.assertEquals

class SendEmailOtpUseCaseTest {
    @Test
    fun `이전 발송 실패가 이후 발송의 코드를 삭제하지 않는다`() {
        val repository = FakeEmailOtpRepository()
        val sender = object : app.xquare.xquareopenapi.domain.auth.mail.OtpMailSender {
            override fun send(email: String, otp: String) {
                repository.save(email, "new-code", java.time.Duration.ofSeconds(300))
                throw IllegalStateException("메일 장애")
            }
        }
        val error = kotlin.test.assertFailsWith<app.xquare.xquareopenapi.global.error.XquareException> {
            SendEmailOtpUseCase(repository, sender, SecureRandom()).execute(EmailOtpSendRequest("example@dsm.hs.kr"))
        }
        assertEquals(app.xquare.xquareopenapi.global.error.ErrorCode.EMAIL_SEND_FAILED, error.errorCode)
        assertEquals("new-code", repository.otps["example@dsm.hs.kr"])
    }

    @Test
    fun `난수 범위는 100000 이상 1000000 미만이며 양 끝값도 그대로 발송한다`() {
        for (otp in listOf(100000, 999999)) {
            val random = mock(SecureRandom::class.java)
            `when`(random.nextInt(100000, 1000000)).thenReturn(otp)
            val repository = FakeEmailOtpRepository()
            val mail = FakeOtpMailSender()
            SendEmailOtpUseCase(repository, mail, random).execute(EmailOtpSendRequest("example@dsm.hs.kr"))
            verify(random).nextInt(100000, 1000000)
            assertEquals(otp.toString(), repository.otps["example@dsm.hs.kr"])
            assertEquals(otp.toString(), mail.messages.single().second)
        }
    }
}
