package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.SignupRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.TokenResponse
import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.domain.user.UserUniqueConstraint
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
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

        val user = save(
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

    // 위의 중복 검사와 저장 사이에 같은 값으로 먼저 가입한 요청이 있으면 unique 제약에 걸린다.
    // 이때도 500이 아니라 중복 검사와 같은 409로 응답한다.
    private fun save(user: User): User =
        try {
            userRepository.save(user)
        } catch (e: DataIntegrityViolationException) {
            throw XquareException(UserUniqueConstraint.violatedBy(e)?.errorCode ?: throw e)
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
        refreshTokenRepository.save(userId, refreshToken.value, refreshToken.ttl)
        return TokenResponse(
            accessToken = jwtProvider.generateAccessToken(userId, user.role),
            refreshToken = refreshToken.value,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(SignupUseCase::class.java)
    }
}
