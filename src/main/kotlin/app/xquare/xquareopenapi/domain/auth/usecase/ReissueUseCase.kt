package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.ReissueRequest
import app.xquare.xquareopenapi.domain.auth.presentation.dto.TokenResponse
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ReissueUseCase(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProvider: JwtProvider,
) {

    fun execute(request: ReissueRequest): TokenResponse {
        val userId = jwtProvider.parseRefreshToken(request.refreshToken) ?: throw invalidRefreshToken()
        val user = userRepository.findByIdOrNull(userId) ?: throw invalidRefreshToken()

        // 저장된 토큰이 요청 토큰과 같을 때만 바꾼다. 이미 재발급에 썼거나 다시 로그인해서 바뀐 토큰은 여기서 걸린다.
        val refreshToken = jwtProvider.generateRefreshToken(userId)
        if (!refreshTokenRepository.replace(userId, request.refreshToken, refreshToken.value, refreshToken.ttl)) {
            throw invalidRefreshToken()
        }
        return TokenResponse(
            accessToken = jwtProvider.generateAccessToken(userId, user.role),
            refreshToken = refreshToken.value,
        )
    }

    private fun invalidRefreshToken() = XquareException(ErrorCode.INVALID_REFRESH_TOKEN)
}
