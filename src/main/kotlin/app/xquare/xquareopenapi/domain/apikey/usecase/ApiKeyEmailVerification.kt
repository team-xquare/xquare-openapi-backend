package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.usecase.normalizeEmail
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

// 활용신청·재발급 전에 로그인한 사용자가 자기 이메일을 방금 인증했는지 확인한다.
// 이메일 OTP 인증 API(POST /auth/email/otp/verify)가 만든 email_verified_token을 회원가입과 같은 방식으로 쓴다.
@Component
class ApiKeyEmailVerification(
    private val userRepository: UserRepository,
    private val emailVerifiedTokenRepository: EmailVerifiedTokenRepository,
) {

    // 토큰이 없거나 만료됐거나, 다른 이메일로 받은 토큰이면 403 EMAIL_NOT_VERIFIED.
    // 트랜잭션 안에서 불러야 한다. 커밋된 뒤에 토큰을 지워서, 신청이 롤백되면 같은 토큰으로 다시 시도할 수 있다.
    fun verify(userId: Long, emailVerifiedToken: String) {
        // a?.b ?: c로 쓰면 null이 될 수 없는 email에 대한 두 번째 null 검사가 생겨 죽일 수 없는 뮤턴트가 남는다.
        val user = userRepository.findByIdOrNull(userId) ?: throw XquareException(ErrorCode.EMAIL_NOT_VERIFIED)
        // OTP 인증은 소문자로 정규화한 이메일을 토큰에 저장하지만, 정규화 전에 가입한 계정은 대문자가 섞인 원문 그대로다.
        // 회원가입과 같은 normalizeEmail(앞뒤 공백 제거·소문자화)로 양쪽을 맞춰 비교한다. 토큰이 없으면 계속 거절한다.
        val verifiedEmail = emailVerifiedTokenRepository.findEmailByToken(emailVerifiedToken)
        if (verifiedEmail == null || normalizeEmail(verifiedEmail) != normalizeEmail(user.email)) {
            throw XquareException(ErrorCode.EMAIL_NOT_VERIFIED)
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                // 지우지 못해도 신청은 끝났고 토큰은 600초 뒤 만료되므로 경고만 남긴다.
                try {
                    emailVerifiedTokenRepository.delete(emailVerifiedToken)
                } catch (e: Exception) {
                    log.warn("이메일 인증 토큰 삭제 실패", e)
                }
            }
        })
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApiKeyEmailVerification::class.java)
    }
}
