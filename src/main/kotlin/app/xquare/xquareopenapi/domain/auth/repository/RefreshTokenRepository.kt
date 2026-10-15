package app.xquare.xquareopenapi.domain.auth.repository

import java.time.Duration

interface RefreshTokenRepository {
    fun save(userId: Long, refreshToken: String, ttl: Duration)
}
