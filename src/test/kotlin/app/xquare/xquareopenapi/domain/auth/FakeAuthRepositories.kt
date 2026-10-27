package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration
import app.xquare.xquareopenapi.domain.auth.repository.EmailOtpRepository
import app.xquare.xquareopenapi.domain.auth.repository.OtpVerification
import app.xquare.xquareopenapi.domain.auth.mail.OtpMailSender

// Redis 없이 인증 API를 테스트하려고 Redis 저장소 대신 끼운다.
@TestConfiguration
class FakeAuthRepositories {
    @Bean
    @Primary
    fun fakeEmailOtpRepository() = FakeEmailOtpRepository()

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
    override fun save(token: String, email: String, ttl: Duration) {
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

class FakeEmailOtpRepository : EmailOtpRepository {
    val otps = mutableMapOf<String, String>()
    val attempts = mutableMapOf<String, Int>()
    val ttls = mutableMapOf<String, Duration>()
    var failOnSave = false
    var failOnDelete = false
    override fun save(email: String, otp: String, ttl: Duration) {
        attempts.remove(email)
        otps[email] = otp
        ttls[email] = ttl
        if (failOnSave) throw IllegalStateException("Redis 저장 실패")
    }
    override fun consume(email: String, otp: String): OtpVerification {
        val current = otps[email] ?: return OtpVerification.NOT_FOUND
        if (current != otp) {
            attempts[email] = (attempts[email] ?: 0) + 1
            if (attempts.getValue(email) >= 5) {
                otps.remove(email)
                attempts.remove(email)
            }
            return OtpVerification.MISMATCH
        }
        attempts.remove(email)
        otps.remove(email)
        return OtpVerification.MATCHED
    }
    override fun delete(email: String, otp: String) {
        if (failOnDelete) throw IllegalStateException("Redis 삭제 실패")
        if (otps[email] == otp) {
            otps.remove(email)
            attempts.remove(email)
        }
    }
    fun clear() {
        otps.clear()
        attempts.clear()
        ttls.clear()
        failOnSave = false
        failOnDelete = false
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
