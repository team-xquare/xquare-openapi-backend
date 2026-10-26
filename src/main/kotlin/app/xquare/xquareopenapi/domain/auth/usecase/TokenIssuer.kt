package app.xquare.xquareopenapi.domain.auth.usecase

import app.xquare.xquareopenapi.domain.auth.presentation.dto.TokenResponse
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import org.springframework.stereotype.Component

// 회원가입과 로그인이 같은 방식으로 토큰을 발급한다. 저장하면 이전 리프레시 토큰은 덮어써져 폐기된다.
@Component
class TokenIssuer(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProvider: JwtProvider,
) {

    fun issue(user: User): TokenResponse {
        val userId = user.id!!
        val refreshToken = jwtProvider.generateRefreshToken(userId)
        refreshTokenRepository.save(userId, refreshToken.value, refreshToken.ttl)
        return TokenResponse(
            accessToken = jwtProvider.generateAccessToken(userId, user.role),
            refreshToken = refreshToken.value,
        )
    }
}
