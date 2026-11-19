package app.xquare.xquareopenapi.domain.auth.repository

import app.xquare.xquareopenapi.global.otp.OtpProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.concurrent.TimeUnit

@Repository
class RedisEmailOtpRepository(
    private val redisTemplate: StringRedisTemplate,
    private val properties: OtpProperties,
) : EmailOtpRepository {
    // 제한 확인, 횟수 증가, 저장을 한 번에 실행해 동시 요청이 상한을 넘지 못하게 한다.
    override fun save(email: String, ip: String, otp: String, ttl: Duration): OtpSendLimit? {
        val result = redisTemplate.execute(
            SAVE_SCRIPT,
            listOf(key(email), attemptsKey(email), cooldownKey(email), sendCountKey(email), ipSendCountKey(ip)),
            otp,
            ttl.toMillis().toString(),
            properties.resendCooldown.toMillis().toString(),
            properties.sendWindow.toMillis().toString(),
            properties.maxSendsPerEmail.toString(),
            properties.maxSendsPerIp.toString(),
            properties.maxAttempts.toString(),
        )
        val (reason, limitKey) = when (result) {
            0L -> return null
            1L -> OtpSendRejection.ATTEMPTS_EXCEEDED to attemptsKey(email)
            2L -> OtpSendRejection.EMAIL_LIMIT to sendCountKey(email)
            3L -> OtpSendRejection.IP_LIMIT to ipSendCountKey(ip)
            4L -> OtpSendRejection.COOLDOWN to cooldownKey(email)
            else -> throw IllegalStateException("OTP 스크립트 실행 실패")
        }
        // 스크립트 뒤에 따로 읽어서 그 사이 만료됐을 수 있다(-2). Retry-After는 안내용이라 최소 1ms로 둔다.
        val ttlMillis = redisTemplate.getExpire(limitKey, TimeUnit.MILLISECONDS)
        return OtpSendLimit(reason, Duration.ofMillis(maxOf(ttlMillis, 1L)))
    }

    // 비교와 삭제를 한 번에 실행해 같은 OTP로 토큰 두 개가 발급되지 않게 한다.
    override fun consume(email: String, otp: String): OtpVerification =
        when (
            redisTemplate.execute(
                CONSUME_SCRIPT,
                listOf(key(email), attemptsKey(email)),
                otp,
                properties.maxAttempts.toString(),
                properties.attemptWindow.toMillis().toString(),
            )
        ) {
            0L -> OtpVerification.NOT_FOUND
            1L -> OtpVerification.MATCHED
            2L -> OtpVerification.MISMATCH
            else -> throw IllegalStateException("OTP 스크립트 실행 실패")
        }

    override fun delete(email: String, otp: String) {
        redisTemplate.execute(DELETE_SCRIPT, listOf(key(email)), otp)
    }

    private fun key(email: String) = "email_otp:$email"

    private fun attemptsKey(email: String) = "email_otp_attempts:$email"

    private fun cooldownKey(email: String) = "email_otp_cooldown:$email"

    private fun sendCountKey(email: String) = "email_otp_send_count:$email"

    private fun ipSendCountKey(ip: String) = "email_otp_ip_send_count:$ip"

    companion object {
        // 오입력 횟수가 상한이면 발송을 막는다. 상한에 닿는 순간 OTP를 지우므로 OTP가 남아 있으면 항상 상한 미만이다.
        private val SAVE_SCRIPT = RedisScript.of(
            """
            local function count(key) return tonumber(redis.call('GET', key) or '0') end
            if count(KEYS[2]) >= tonumber(ARGV[7]) then return 1 end
            if count(KEYS[4]) >= tonumber(ARGV[5]) then return 2 end
            if count(KEYS[5]) >= tonumber(ARGV[6]) then return 3 end
            if redis.call('EXISTS', KEYS[3]) == 1 then return 4 end
            for i = 4, 5 do
                if redis.call('INCR', KEYS[i]) == 1 then redis.call('PEXPIRE', KEYS[i], ARGV[4]) end
            end
            redis.call('SET', KEYS[3], '1', 'PX', ARGV[3])
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return 0
            """.trimIndent(),
            Long::class.java,
        )
        private val DELETE_SCRIPT = RedisScript.of(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
        // 오입력 횟수는 재발송해도 지우지 않고, 첫 오입력부터 창 길이 동안 센다.
        private val CONSUME_SCRIPT = RedisScript.of(
            """
            local current = redis.call('GET', KEYS[1])
            if not current then return 0 end
            if current ~= ARGV[1] then
                local attempts = redis.call('INCR', KEYS[2])
                if attempts == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[3]) end
                if attempts >= tonumber(ARGV[2]) then redis.call('DEL', KEYS[1]) end
                return 2
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}
