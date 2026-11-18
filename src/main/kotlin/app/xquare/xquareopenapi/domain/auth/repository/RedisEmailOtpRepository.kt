package app.xquare.xquareopenapi.domain.auth.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisEmailOtpRepository(private val redisTemplate: StringRedisTemplate) : EmailOtpRepository {
    override fun save(email: String, otp: String, ttl: Duration) {
        redisTemplate.execute(SAVE_SCRIPT, listOf(key(email), attemptsKey(email)), otp, ttl.toMillis().toString())
    }

    // 비교와 삭제를 한 번에 실행해 같은 OTP로 토큰 두 개가 발급되지 않게 한다.
    override fun consume(email: String, otp: String): OtpVerification =
        when (redisTemplate.execute(CONSUME_SCRIPT, listOf(key(email), attemptsKey(email)), otp)) {
            0L -> OtpVerification.NOT_FOUND
            1L -> OtpVerification.MATCHED
            2L -> OtpVerification.MISMATCH
            else -> throw IllegalStateException("OTP 스크립트 실행 실패")
        }

    override fun delete(email: String, otp: String) {
        redisTemplate.execute(DELETE_SCRIPT, listOf(key(email), attemptsKey(email)), otp)
    }

    private fun key(email: String) = "email_otp:$email"

    private fun attemptsKey(email: String) = "email_otp_attempts:$email"

    companion object {
        private val SAVE_SCRIPT = RedisScript.of(
            """
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('DEL', KEYS[2])
            return 1
            """.trimIndent(),
            Long::class.java,
        )
        private val DELETE_SCRIPT = RedisScript.of(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1], KEYS[2])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
        private val CONSUME_SCRIPT = RedisScript.of(
            """
            local current = redis.call('GET', KEYS[1])
            if not current then return 0 end
            if current ~= ARGV[1] then
                local attempts = redis.call('INCR', KEYS[2])
                redis.call('PEXPIRE', KEYS[2], redis.call('PTTL', KEYS[1]))
                if attempts >= 5 then redis.call('DEL', KEYS[1], KEYS[2]) end
                return 2
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}
