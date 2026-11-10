package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.RedisConnectionFailureException
import java.time.Duration
import app.xquare.xquareopenapi.domain.auth.repository.EmailOtpRepository
import app.xquare.xquareopenapi.domain.auth.repository.OtpVerification
import app.xquare.xquareopenapi.domain.auth.repository.OtpSendLimit
import app.xquare.xquareopenapi.domain.auth.repository.OtpSendRejection
import app.xquare.xquareopenapi.global.otp.OtpProperties
import java.time.Instant
import app.xquare.xquareopenapi.domain.auth.mail.OtpMailSender

// Redis 없이 인증 API를 테스트하려고 Redis 저장소 대신 끼운다.
@TestConfiguration
class FakeAuthRepositories {
    @Bean
    @Primary
    fun fakeEmailOtpRepository(properties: OtpProperties) = FakeEmailOtpRepository(properties)

    @Bean
    @Primary
    fun fakeOtpMailSender() = FakeOtpMailSender()

    @Bean
    @Primary
    fun fakeEmailVerifiedTokenRepository() = FakeEmailVerifiedTokenRepository()

    @Bean
    @Primary
    fun fakeRefreshTokenRepository() = FakeRefreshTokenRepository()
}

class FakeEmailVerifiedTokenRepository : EmailVerifiedTokenRepository {
    val ttls = mutableMapOf<String, Duration>()
    var failOnSave = false
    override fun save(token: String, email: String, ttl: Duration) {
        if (failOnSave) throw RedisConnectionFailureException("Redis 장애")
        tokens[token] = email
        ttls[token] = ttl
    }

    val tokens = mutableMapOf<String, String>()

    override fun findEmailByToken(token: String): String? = tokens[token]

    var failOnDelete = false

    override fun delete(token: String) {
        if (failOnDelete) throw IllegalStateException("Redis 장애")
        tokens.remove(token)
    }
}

class FakeRefreshTokenRepository : RefreshTokenRepository {
    val tokens = mutableMapOf<Long, String>()
    val ttls = mutableMapOf<Long, Duration>()

    var failOnSave = false

    override fun save(userId: Long, refreshToken: String, ttl: Duration) {
        if (failOnSave) throw IllegalStateException("Redis 장애")
        tokens[userId] = refreshToken
        ttls[userId] = ttl
    }

    override fun replace(userId: Long, expected: String, refreshToken: String, ttl: Duration): Boolean {
        if (tokens[userId] != expected) return false
        tokens[userId] = refreshToken
        ttls[userId] = ttl
        return true
    }

    fun clear() {
        tokens.clear()
        ttls.clear()
        failOnSave = false
    }
}

// Spring 없이 FakeEmailOtpRepository를 만들 때 쓴다. 값은 운영 기본값과 같다.
val TEST_OTP_PROPERTIES = OtpProperties(Duration.ofSeconds(60), Duration.ofHours(1), 5, 30, Duration.ofHours(1), 5)

// RedisEmailOtpRepository와 같은 규칙으로 동작한다. 키 이름도 Redis 키와 맞췄다.
// 창 만료를 기다리지 않도록 테스트가 now를 직접 움직인다. OTP 자체의 TTL 만료는 흉내 내지 않는다.
class FakeEmailOtpRepository(private val properties: OtpProperties) : EmailOtpRepository {
    val otps = mutableMapOf<String, String>()
    val ttls = mutableMapOf<String, Duration>()
    var failOnSave = false
    var failOnDelete = false
    var failOnConsume = false
    var now: Instant = Instant.EPOCH
    // 저장소에 넘어온 IP 키. 실제 서버를 띄운 테스트에서 어떤 IP로 셌는지 확인할 때 쓴다.
    val ips = mutableListOf<String>()
    private val counters = mutableMapOf<String, Pair<Int, Instant>>()

    override fun save(email: String, ip: String, otp: String, ttl: Duration): OtpSendLimit? {
        ips.add(ip)
        val limit = when {
            count("email_otp_attempts:$email") >= properties.maxAttempts ->
                limit(OtpSendRejection.ATTEMPTS_EXCEEDED, "email_otp_attempts:$email")
            count("email_otp_send_count:$email") >= properties.maxSendsPerEmail ->
                limit(OtpSendRejection.EMAIL_LIMIT, "email_otp_send_count:$email")
            count("email_otp_ip_send_count:$ip") >= properties.maxSendsPerIp ->
                limit(OtpSendRejection.IP_LIMIT, "email_otp_ip_send_count:$ip")
            count("email_otp_cooldown:$email") > 0 -> limit(OtpSendRejection.COOLDOWN, "email_otp_cooldown:$email")
            else -> null
        }
        if (limit != null) return limit
        increment("email_otp_send_count:$email", properties.sendWindow)
        increment("email_otp_ip_send_count:$ip", properties.sendWindow)
        counters["email_otp_cooldown:$email"] = 1 to now.plus(properties.resendCooldown)
        otps[email] = otp
        ttls[email] = ttl
        if (failOnSave) throw IllegalStateException("Redis 저장 실패")
        return null
    }

    override fun consume(email: String, otp: String): OtpVerification {
        if (failOnConsume) throw RedisConnectionFailureException("Redis 장애")
        val current = otps[email] ?: return OtpVerification.NOT_FOUND
        if (current != otp) {
            if (increment("email_otp_attempts:$email", properties.attemptWindow) >= properties.maxAttempts) {
                otps.remove(email)
            }
            return OtpVerification.MISMATCH
        }
        counters.remove("email_otp_attempts:$email")
        otps.remove(email)
        return OtpVerification.MATCHED
    }

    override fun delete(email: String, otp: String) {
        if (failOnDelete) throw IllegalStateException("Redis 삭제 실패")
        if (otps[email] == otp) otps.remove(email)
    }

    fun attempts(email: String) = count("email_otp_attempts:$email")

    private fun live(key: String) = counters[key]?.takeIf { it.second > now }

    private fun count(key: String) = live(key)?.first ?: 0

    private fun increment(key: String, window: Duration): Int {
        val current = live(key)
        val next = (current?.first ?: 0) + 1
        counters[key] = next to (current?.second ?: now.plus(window))
        return next
    }

    private fun limit(reason: OtpSendRejection, key: String) =
        OtpSendLimit(reason, Duration.between(now, counters.getValue(key).second))

    fun clear() {
        otps.clear()
        ttls.clear()
        counters.clear()
        ips.clear()
        now = Instant.EPOCH
        failOnSave = false
        failOnDelete = false
        failOnConsume = false
    }
}

class FakeOtpMailSender : OtpMailSender {
    val messages = mutableListOf<Pair<String, String>>()
    var failOnSend = false
    override fun send(email: String, otp: String) {
        if (failOnSend) throw IllegalStateException("SMTP 장애")
        messages.add(email to otp)
    }
    fun clear() {
        messages.clear()
        failOnSend = false
    }
}
