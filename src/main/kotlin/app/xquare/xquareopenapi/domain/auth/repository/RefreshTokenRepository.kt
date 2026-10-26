package app.xquare.xquareopenapi.domain.auth.repository

import java.time.Duration

interface RefreshTokenRepository {
    fun save(userId: Long, refreshToken: String, ttl: Duration)

    // 저장된 토큰이 expected일 때만 refreshToken으로 바꾸고 true를 돌려준다.
    fun replace(userId: Long, expected: String, refreshToken: String, ttl: Duration): Boolean
}
