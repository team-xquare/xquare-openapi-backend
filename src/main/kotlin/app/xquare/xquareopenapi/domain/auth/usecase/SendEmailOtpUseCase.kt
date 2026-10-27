package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailOtpSendRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.MessageResponse
import app.xquare.xquareopenapi.domain.auth.repository.EmailOtpRepository
import app.xquare.xquareopenapi.domain.auth.mail.OtpMailSender
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration

@Service
class SendEmailOtpUseCase(
    private val emailOtpRepository: EmailOtpRepository,
    private val mailSender: OtpMailSender,
    private val random: SecureRandom,
) {
    fun execute(request: EmailOtpSendRequest): MessageResponse {
        validateOtpEmail(request.email)
        val otp = random.nextInt(100000, 1000000).toString()
        try {
            emailOtpRepository.save(request.email, otp, Duration.ofSeconds(300))
            mailSender.send(request.email, otp)
        } catch (e: Exception) {
            log.error("이메일 OTP 발송 실패", e)
            // 저장 응답이 유실됐을 수도 있어 저장 실패 때도 정리를 시도한다.
            try {
                emailOtpRepository.delete(request.email, otp)
            } catch (cleanup: Exception) {
                log.warn("이메일 OTP 삭제 실패", cleanup)
            }
            throw XquareException(ErrorCode.EMAIL_SEND_FAILED)
        }
        return MessageResponse("인증코드를 발송했습니다.")
    }

    companion object {
        private val log = LoggerFactory.getLogger(SendEmailOtpUseCase::class.java)
    }
}
