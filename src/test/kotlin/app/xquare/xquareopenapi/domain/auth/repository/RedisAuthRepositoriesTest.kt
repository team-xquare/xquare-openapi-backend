package app.xquare.xquareopenapi.domain.auth.repository

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataRedisTest
@Testcontainers(disabledWithoutDocker = true)
@Import(RedisEmailVerifiedTokenRepository::class, RedisRefreshTokenRepository::class)
class RedisAuthRepositoriesTest @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
    private val emailVerifiedTokenRepository: RedisEmailVerifiedTokenRepository,
    private val refreshTokenRepository: RedisRefreshTokenRepository,
) {

    @AfterEach
    fun tearDown() {
        redisTemplate.connectionFactory!!.connection.serverCommands().flushAll()
    }

    @Test
    fun `이메일 OTP 인증이 저장한 키에서 이메일을 읽는다`() {
        redisTemplate.opsForValue().set("email_verified_token:token-1", "example@dsm.hs.kr")

        assertEquals("example@dsm.hs.kr", emailVerifiedTokenRepository.findEmailByToken("token-1"))
        assertNull(emailVerifiedTokenRepository.findEmailByToken("token-2"))
    }

    @Test
    fun `이메일 인증 토큰을 지운다`() {
        redisTemplate.opsForValue().set("email_verified_token:token-1", "example@dsm.hs.kr")

        emailVerifiedTokenRepository.delete("token-1")

        assertEquals(false, redisTemplate.hasKey("email_verified_token:token-1"))
    }

    @Test
    fun `리프레시 토큰을 사용자 id 키에 TTL과 함께 저장한다`() {
        refreshTokenRepository.save(7L, "refresh-token", Duration.ofDays(14))

        assertEquals("refresh-token", redisTemplate.opsForValue().get("refresh_token:7"))
        val ttl = redisTemplate.getExpire("refresh_token:7")
        assertTrue(ttl in 1..Duration.ofDays(14).seconds, "ttl=$ttl")
    }

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7.4-alpine").withExposedPorts(6379)
    }
}
