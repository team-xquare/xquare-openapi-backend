package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailOtpSendRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.MessageResponse
import app.xquare.xquareopenapi.domain.auth.repository.EmailOtpRepository
import app.xquare.xquareopenapi.domain.auth.repository.OtpSendRejection
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
    fun execute(request: EmailOtpSendRequest, ip: String): MessageResponse {
        validateOtpEmail(request.email)
        val otp = random.nextInt(100000, 1000000).toString()
        val limit = try {
            emailOtpRepository.save(request.email, ip, otp, Duration.ofSeconds(300))
        } catch (e: Exception) {
            throw sendFailed(request.email, otp, e)
        }
        if (limit != null) {
            val errorCode = when (limit.reason) {
                OtpSendRejection.ATTEMPTS_EXCEEDED -> ErrorCode.OTP_ATTEMPTS_EXCEEDED
                OtpSendRejection.EMAIL_LIMIT, OtpSendRejection.IP_LIMIT -> ErrorCode.OTP_SEND_LIMIT_EXCEEDED
                OtpSendRejection.COOLDOWN -> ErrorCode.OTP_RESEND_TOO_SOON
            }
            throw XquareException(errorCode, limit.retryAfter)
        }
        try {
            mailSender.send(request.email, otp)
        } catch (e: Exception) {
            throw sendFailed(request.email, otp, e)
        }
        return MessageResponse("인증코드를 발송했습니다.")
    }

    // 쿨다운과 발송 횟수는 되돌리지 않는다. 타임아웃이면 메일이 실제로 나갔을 수 있고,
    // 메일 서비스 장애 중에 바로 재시도가 몰리는 것도 막는다.
    // 반환형을 Nothing으로 두면 호출부에 도달할 수 없는 예외 생성 코드가 생겨 뮤턴트가 검사되지 않는다. 그래서 예외를 돌려주고 호출부에서 던진다.
    private fun sendFailed(email: String, otp: String, e: Exception): XquareException {
        log.error("이메일 OTP 발송 실패", e)
        // 저장 응답이 유실됐을 수도 있어 저장 실패 때도 정리를 시도한다.
        try {
            emailOtpRepository.delete(email, otp)
        } catch (cleanup: Exception) {
            log.warn("이메일 OTP 삭제 실패", cleanup)
        }
        return XquareException(ErrorCode.EMAIL_SEND_FAILED)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SendEmailOtpUseCase::class.java)
    }
}
