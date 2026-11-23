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
        val payload = jwtProvider.parseRefreshToken(request.refreshToken) ?: throw invalidRefreshToken()
        val userId = payload.userId
        val user = userRepository.findByIdOrNull(userId) ?: throw invalidRefreshToken()
        // 발급 뒤 비밀번호가 바뀌었으면 거절한다. 재설정이 리프레시 토큰을 지운 뒤에, 옛 비밀번호로 검증을 마친 로그인이
        // 새 토큰을 저장해도 그 토큰은 옛 비밀번호 해시로 발급돼 여기서 걸린다.
        if (payload.passwordFingerprint != jwtProvider.passwordFingerprint(user.password)) {
            throw invalidRefreshToken()
        }

        // 저장된 토큰이 요청 토큰과 같을 때만 바꾼다. 이미 재발급에 썼거나 다시 로그인해서 바뀐 토큰은 여기서 걸린다.
        val refreshToken = jwtProvider.generateRefreshToken(userId, user.password)
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
