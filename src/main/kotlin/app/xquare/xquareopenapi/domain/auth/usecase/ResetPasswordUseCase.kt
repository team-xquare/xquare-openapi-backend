package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.MessageResponse
import app.xquare.xquareopenapi.domain.auth.presentation.dto.PasswordResetRequest
import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

// 이메일 OTP로 받은 인증 토큰으로 비밀번호를 바꾼다. 토큰은 회원가입과 같은 것을 쓴다(용도 구분 없음).
@Service
class ResetPasswordUseCase(
    private val userRepository: UserRepository,
    private val emailVerifiedTokenRepository: EmailVerifiedTokenRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun execute(request: PasswordResetRequest): MessageResponse {
        val email = emailVerifiedTokenRepository.findEmailByToken(request.emailVerifiedToken)
            ?: throw XquareException(ErrorCode.EMAIL_NOT_VERIFIED)
        val user = userRepository.findByEmail(email) ?: throw XquareException(ErrorCode.USER_NOT_FOUND)

        user.changePassword(passwordEncoder.encode(request.newPassword)!!)
        // 커밋 전에 지운다. 지우기에 실패하면 비밀번호 변경도 롤백되어, 기존 세션이 살아 있는 채로 비밀번호만 바뀌는 일이 없다.
        refreshTokenRepository.delete(user.id!!)
        deleteEmailVerifiedTokenAfterCommit(request.emailVerifiedToken)

        return MessageResponse("비밀번호가 변경되었습니다.")
    }

    // 롤백되면 같은 토큰으로 다시 시도할 수 있도록 커밋된 뒤에 지운다(회원가입과 같은 방식).
    // 지우기에 실패해도 비밀번호는 이미 바뀌었고, 남은 토큰은 메일함 주인만 가진 것이라 경고만 남긴다.
    private fun deleteEmailVerifiedTokenAfterCommit(token: String) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                try {
                    emailVerifiedTokenRepository.delete(token)
                } catch (e: Exception) {
                    log.warn("이메일 인증 토큰 삭제 실패", e)
                }
            }
        })
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResetPasswordUseCase::class.java)
    }
}
