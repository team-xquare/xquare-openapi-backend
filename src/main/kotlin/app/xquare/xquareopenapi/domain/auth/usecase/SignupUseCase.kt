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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
        emailVerifiedTokenRepository.delete(request.emailVerifiedToken)

        return issueTokens(user)
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
}
