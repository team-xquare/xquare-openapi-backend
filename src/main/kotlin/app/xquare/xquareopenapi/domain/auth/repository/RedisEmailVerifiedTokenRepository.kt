package app.xquare.xquareopenapi.domain.auth.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

/**
 * 이메일 OTP 인증 API가 `email_verified_token:{token}` -> email 형태로 600초 TTL로 저장한 값을 읽는다.
 */
@Repository
class RedisEmailVerifiedTokenRepository(
    private val redisTemplate: StringRedisTemplate,
) : EmailVerifiedTokenRepository {

    override fun findEmailByToken(token: String): String? =
        redisTemplate.opsForValue().get(key(token))

    override fun delete(token: String) {
        redisTemplate.delete(key(token))
    }

    private fun key(token: String) = "$KEY_PREFIX$token"

    companion object {
        const val KEY_PREFIX = "email_verified_token:"
    }
}
