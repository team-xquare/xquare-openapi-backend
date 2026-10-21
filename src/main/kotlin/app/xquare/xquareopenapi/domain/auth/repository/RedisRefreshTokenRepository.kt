package app.xquare.xquareopenapi.domain.auth.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisRefreshTokenRepository(
    private val redisTemplate: StringRedisTemplate,
) : RefreshTokenRepository {

    override fun save(userId: Long, refreshToken: String, ttl: Duration) {
        redisTemplate.opsForValue().set(key(userId), refreshToken, ttl)
    }

    // 비교와 교체를 스크립트 하나로 실행한다. 따로 하면 같은 토큰으로 동시에 재발급하거나
    // 그 사이에 다시 로그인했을 때, 이미 폐기된 토큰으로 새 토큰을 덮어쓸 수 있다.
    override fun replace(userId: Long, expected: String, refreshToken: String, ttl: Duration): Boolean =
        redisTemplate.execute(REPLACE_SCRIPT, listOf(key(userId)), expected, refreshToken, ttl.toMillis().toString()) == 1L

    private fun key(userId: Long) = "$KEY_PREFIX$userId"

    companion object {
        const val KEY_PREFIX = "refresh_token:"

        private val REPLACE_SCRIPT = RedisScript.of(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
            end
            return 0
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}
