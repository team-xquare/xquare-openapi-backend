package app.xquare.xquareopenapi.domain.auth.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisRefreshTokenRepository(
    private val redisTemplate: StringRedisTemplate,
) : RefreshTokenRepository {

    override fun save(userId: Long, refreshToken: String, ttl: Duration) {
        redisTemplate.opsForValue().set("$KEY_PREFIX$userId", refreshToken, ttl)
    }

    companion object {
        const val KEY_PREFIX = "refresh_token:"
    }
}
