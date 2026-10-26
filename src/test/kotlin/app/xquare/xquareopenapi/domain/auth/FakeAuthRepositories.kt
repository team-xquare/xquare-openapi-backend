package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.auth.repository.EmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.auth.repository.RefreshTokenRepository
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration

// Redis 없이 인증 API를 테스트하려고 Redis 저장소 대신 끼운다.
@TestConfiguration
class FakeAuthRepositories {
    @Bean
    @Primary
    fun fakeEmailVerifiedTokenRepository() = FakeEmailVerifiedTokenRepository()

    @Bean
    @Primary
    fun fakeRefreshTokenRepository() = FakeRefreshTokenRepository()
}

class FakeEmailVerifiedTokenRepository : EmailVerifiedTokenRepository {
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
