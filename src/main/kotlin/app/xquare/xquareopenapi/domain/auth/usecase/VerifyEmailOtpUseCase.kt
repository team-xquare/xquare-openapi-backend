package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailOtpVerifyRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailVerifiedTokenResponse
import app.xquare.xquareopenapi.domain.auth.repository.EmailOtpRepository
import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.OtpVerification
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class VerifyEmailOtpUseCase(
    private val emailOtpRepository: EmailOtpRepository,
    private val emailVerifiedTokenRepository: EmailVerifiedTokenRepository,
) {
    fun execute(request: EmailOtpVerifyRequest): EmailVerifiedTokenResponse {
        validateOtpEmail(request.email)
        // 토큰을 먼저 저장하고 OTP를 소모한다. 반대 순서면 OTP를 지운 뒤 토큰 저장이 실패했을 때 코드를 다시 받아야 한다.
        // 토큰은 OTP가 맞을 때만 응답으로 나가므로, 틀렸거나 소모에 실패하면 지운다.
        val token = UUID.randomUUID().toString()
        emailVerifiedTokenRepository.save(token, request.email, Duration.ofSeconds(600))
        val verification = try {
            emailOtpRepository.consume(request.email, request.otp)
        } catch (e: Exception) {
            deleteToken(token)
            throw e
        }
        when (verification) {
            OtpVerification.NOT_FOUND -> throw rejected(token, ErrorCode.OTP_NOT_FOUND)
            OtpVerification.MISMATCH -> throw rejected(token, ErrorCode.OTP_MISMATCH)
            OtpVerification.MATCHED -> Unit
        }
        return EmailVerifiedTokenResponse(token)
    }

    private fun rejected(token: String, errorCode: ErrorCode): XquareException {
        deleteToken(token)
        return XquareException(errorCode)
    }

    // 지우지 못해도 아무에게도 알려 주지 않은 토큰이라 쓸 수 없고 600초 뒤 만료되므로 경고만 남긴다.
    private fun deleteToken(token: String) {
        try {
            emailVerifiedTokenRepository.delete(token)
        } catch (e: Exception) {
            log.warn("쓰지 않은 이메일 인증 토큰 삭제 실패", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(VerifyEmailOtpUseCase::class.java)
    }
}
