package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.SignupRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.TokenResponse
import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.jwt.JwtProperties
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class SignupUseCase(
    private val userRepository: UserRepository,
    private val emailVerifiedTokenRepository: EmailVerifiedTokenRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val jwtProperties: JwtProperties,
) {

    @Transactional
    fun execute(request: SignupRequest): TokenResponse {
        val verifiedEmail = emailVerifiedTokenRepository.findEmailByToken(request.emailVerifiedToken)
        if (verifiedEmail != request.email) {
            throw XquareException(ErrorCode.EMAIL_NOT_VERIFIED)
        }
        if (userRepository.existsByUsername(request.username)) {
            throw XquareException(ErrorCode.USERNAME_ALREADY_EXISTS)
        }
        if (userRepository.existsByEmail(request.email)) {
            throw XquareException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        val user = userRepository.save(
            User(
                username = request.username,
                password = passwordEncoder.encode(request.password)!!,
                studentNumber = request.studentNumber,
                name = request.name,
                email = request.email,
            ),
        )
        deleteEmailVerifiedTokenAfterCommit(request.emailVerifiedToken)

        return issueTokens(user)
    }

    // 가입이 롤백되면 같은 토큰으로 다시 시도할 수 있도록 커밋된 뒤에 지운다.
    // 지우기에 실패해도 가입은 이미 끝났고, 남은 토큰은 이메일 중복 검사에 막혀 다시 쓸 수 없으므로 경고만 남긴다.
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

    private fun issueTokens(user: User): TokenResponse {
        val userId = user.id!!
        val refreshToken = jwtProvider.generateRefreshToken(userId)
        refreshTokenRepository.save(userId, refreshToken, jwtProperties.refreshExpiration)
        return TokenResponse(
            accessToken = jwtProvider.generateAccessToken(userId, user.role),
            refreshToken = refreshToken,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(SignupUseCase::class.java)
    }
}
