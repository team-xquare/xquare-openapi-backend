package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailOtpVerifyRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.EmailVerifiedTokenResponse
import app.xquare.xquareopenapi.domain.auth.repository.EmailOtpRepository
import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.OtpVerification
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
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
        when (emailOtpRepository.consume(request.email, request.otp)) {
            OtpVerification.NOT_FOUND -> throw XquareException(ErrorCode.OTP_NOT_FOUND)
            OtpVerification.MISMATCH -> throw XquareException(ErrorCode.OTP_MISMATCH)
            OtpVerification.MATCHED -> Unit
        }
        val token = UUID.randomUUID().toString()
        emailVerifiedTokenRepository.save(token, request.email, Duration.ofSeconds(600))
        return EmailVerifiedTokenResponse(token)
    }
}
