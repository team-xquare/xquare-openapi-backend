package app.xquare.xquareopenapi.domain.auth.repository

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import app.xquare.xquareopenapi.global.otp.OtpProperties
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// PIT이 클래스를 재실행할 때 종료된 컨테이너 연결을 캐시에서 재사용하지 않는다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DataRedisTest
@Testcontainers(disabledWithoutDocker = true)
@Import(RedisEmailVerifiedTokenRepository::class, RedisRefreshTokenRepository::class)
class RedisAuthRepositoriesTest @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
    private val emailVerifiedTokenRepository: RedisEmailVerifiedTokenRepository,
    private val refreshTokenRepository: RedisRefreshTokenRepository,
) {
    private val emailOtps = RedisEmailOtpRepository(
        redisTemplate,
        OtpProperties(Duration.ofSeconds(60), Duration.ofHours(1), 3, 4, Duration.ofHours(1), 5),
    )

    private fun save(email: String, otp: String, ttl: Duration = Duration.ofSeconds(300), ip: String = "ip") =
        emailOtps.save(email, ip, otp, ttl)

    // 쿨다운을 기다리지 않고 재발송을 흉내 낸다.
    private fun skipCooldown(email: String) {
        redisTemplate.delete("email_otp_cooldown:$email")
    }

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

    @Test
    fun `저장된 리프레시 토큰이 기대한 값이면 새 토큰과 TTL로 바꾼다`() {
        redisTemplate.opsForValue().set("refresh_token:7", "old-token")

        assertTrue(refreshTokenRepository.replace(7L, "old-token", "new-token", Duration.ofDays(14)))

        assertEquals("new-token", redisTemplate.opsForValue().get("refresh_token:7"))
        val ttl = redisTemplate.getExpire("refresh_token:7")
        assertTrue(ttl in Duration.ofDays(14).seconds - 10..Duration.ofDays(14).seconds, "ttl=$ttl")
    }

    @Test
    fun `저장된 리프레시 토큰이 기대한 값과 다르면 바꾸지 않는다`() {
        redisTemplate.opsForValue().set("refresh_token:7", "newer-token")

        assertFalse(refreshTokenRepository.replace(7L, "old-token", "new-token", Duration.ofDays(14)))
        assertFalse(refreshTokenRepository.replace(8L, "old-token", "new-token", Duration.ofDays(14)))

        assertEquals("newer-token", redisTemplate.opsForValue().get("refresh_token:7"))
        assertEquals(false, redisTemplate.hasKey("refresh_token:8"))
    }

    @Test
    fun `OTP를 이메일 키에 저장하고 재발송은 값과 TTL을 덮어쓴다`() {
        assertNull(save("a@dsm.hs.kr", "111111", Duration.ofSeconds(10)))
        skipCooldown("a@dsm.hs.kr")
        assertNull(save("a@dsm.hs.kr", "222222"))
        assertEquals("222222", redisTemplate.opsForValue().get("email_otp:a@dsm.hs.kr"))
        assertTrue(redisTemplate.getExpire("email_otp:a@dsm.hs.kr") in 295..300)
        emailOtps.delete("a@dsm.hs.kr", "222222")
        assertFalse(redisTemplate.hasKey("email_otp:a@dsm.hs.kr"))
    }

    @Test
    fun `OTP 스크립트는 없음 불일치 성공을 구분하고 성공한 코드만 삭제한다`() {
        assertEquals(OtpVerification.NOT_FOUND, emailOtps.consume("a", "123456"))
        save("a", "123456")
        assertEquals(OtpVerification.MISMATCH, emailOtps.consume("a", "654321"))
        assertEquals("123456", redisTemplate.opsForValue().get("email_otp:a"))
        assertEquals(OtpVerification.MATCHED, emailOtps.consume("a", "123456"))
        assertNull(redisTemplate.opsForValue().get("email_otp:a"))
        assertEquals(OtpVerification.NOT_FOUND, emailOtps.consume("a", "123456"))
    }

    @Test
    fun `동시에 같은 OTP를 소비하면 하나만 성공한다`() {
        save("a", "123456")
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val start = java.util.concurrent.CountDownLatch(1)
            val tasks = (1..2).map {
                executor.submit<OtpVerification> {
                    start.await()
                    emailOtps.consume("a", "123456")
                }
            }
            start.countDown()
            assertEquals(listOf(OtpVerification.MATCHED, OtpVerification.NOT_FOUND).toSet(), tasks.map { it.get() }.toSet())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `인증 토큰을 기존 회원가입 계약대로 600초 저장한다`() {
        emailVerifiedTokenRepository.save("verified", "a@dsm.hs.kr", Duration.ofSeconds(600))
        assertEquals("a@dsm.hs.kr", redisTemplate.opsForValue().get("email_verified_token:verified"))
        assertTrue(redisTemplate.getExpire("email_verified_token:verified") in 595..600)
    }


    @Test
    fun `4회 실패 후 성공하면 횟수 키도 삭제한다`() {
        save("a", "123456")
        repeat(4) { assertEquals(OtpVerification.MISMATCH, emailOtps.consume("a", "wrong")) }
        assertEquals("4", redisTemplate.opsForValue().get("email_otp_attempts:a"))
        assertEquals(OtpVerification.MATCHED, emailOtps.consume("a", "123456"))
        assertFalse(redisTemplate.hasKey("email_otp_attempts:a"))
    }

    @Test
    fun `5회 실패는 코드를 폐기하고 횟수는 남기며 다음 요청은 없음이다`() {
        save("a", "123456")
        repeat(5) { assertEquals(OtpVerification.MISMATCH, emailOtps.consume("a", "wrong")) }
        assertFalse(redisTemplate.hasKey("email_otp:a"))
        assertEquals("5", redisTemplate.opsForValue().get("email_otp_attempts:a"))
        assertEquals(OtpVerification.NOT_FOUND, emailOtps.consume("a", "123456"))
    }

    @Test
    fun `재발송해도 오입력 횟수는 이어지고 상한이면 발송을 막는다`() {
        save("a", "123456")
        repeat(3) { emailOtps.consume("a", "wrong") }
        skipCooldown("a")
        assertNull(save("a", "654321"))
        assertEquals("3", redisTemplate.opsForValue().get("email_otp_attempts:a"))
        repeat(2) { assertEquals(OtpVerification.MISMATCH, emailOtps.consume("a", "wrong")) }
        assertEquals(OtpVerification.NOT_FOUND, emailOtps.consume("a", "654321"))
        skipCooldown("a")
        val limit = save("a", "111111")!!
        assertEquals(OtpSendRejection.ATTEMPTS_EXCEEDED, limit.reason)
        assertTrue(limit.retryAfter.seconds in 3590..3600, "retryAfter=${limit.retryAfter}")
        assertFalse(redisTemplate.hasKey("email_otp:a"))
    }

    @Test
    fun `오입력 횟수 TTL은 첫 오입력부터 창 길이이고 다음 오입력에 늘어나지 않는다`() {
        save("a", "123456")
        emailOtps.consume("a", "wrong")
        assertTrue(redisTemplate.getExpire("email_otp_attempts:a") in 3590..3600)
        redisTemplate.expire("email_otp_attempts:a", Duration.ofSeconds(100))
        emailOtps.consume("a", "wrong")
        assertEquals("2", redisTemplate.opsForValue().get("email_otp_attempts:a"))
        assertTrue(redisTemplate.getExpire("email_otp_attempts:a") in 1..100)
    }

    @Test
    fun `쿨다운 안의 재발송은 저장하지 않고 횟수도 늘리지 않는다`() {
        assertNull(save("a", "123456"))
        val limit = save("a", "654321")!!
        assertEquals(OtpSendRejection.COOLDOWN, limit.reason)
        assertTrue(limit.retryAfter.seconds in 55..60, "retryAfter=${limit.retryAfter}")
        assertEquals("123456", redisTemplate.opsForValue().get("email_otp:a"))
        assertEquals("1", redisTemplate.opsForValue().get("email_otp_send_count:a"))
        assertEquals("1", redisTemplate.opsForValue().get("email_otp_ip_send_count:ip"))
        assertTrue(redisTemplate.getExpire("email_otp_send_count:a") in 3590..3600)
    }

    @Test
    fun `이메일과 IP의 발송 상한을 넘으면 저장하지 않는다`() {
        repeat(3) {
            assertNull(save("a", "12345$it"))
            skipCooldown("a")
        }
        assertEquals(OtpSendRejection.EMAIL_LIMIT, save("a", "999999")?.reason)
        assertEquals("123452", redisTemplate.opsForValue().get("email_otp:a"))
        assertEquals("3", redisTemplate.opsForValue().get("email_otp_send_count:a"))
        assertNull(save("b", "123456"))
        assertEquals(OtpSendRejection.IP_LIMIT, save("c", "123456")?.reason)
        assertFalse(redisTemplate.hasKey("email_otp:c"))
        assertNull(save("c", "123456", ip = "other-ip"))
    }

    @Test
    fun `창이 지나면 다시 발송할 수 있다`() {
        val short = RedisEmailOtpRepository(
            redisTemplate,
            OtpProperties(Duration.ofMillis(200), Duration.ofMillis(300), 1, 1, Duration.ofMillis(300), 1),
        )
        assertNull(short.save("a", "ip", "123456", Duration.ofSeconds(300)))
        assertEquals(OtpVerification.MISMATCH, short.consume("a", "wrong"))
        assertEquals(OtpSendRejection.ATTEMPTS_EXCEEDED, short.save("a", "ip", "654321", Duration.ofSeconds(300))?.reason)
        Thread.sleep(500)
        assertNull(short.save("a", "ip", "654321", Duration.ofSeconds(300)))
        assertEquals(OtpVerification.MATCHED, short.consume("a", "654321"))
    }

    @Test
    fun `실패 정리는 덮어쓴 코드를 보존하고 같은 코드만 삭제하며 횟수는 지우지 않는다`() {
        save("a", "new")
        emailOtps.consume("a", "wrong")
        emailOtps.delete("a", "old")
        assertEquals("new", redisTemplate.opsForValue().get("email_otp:a"))
        assertEquals("1", redisTemplate.opsForValue().get("email_otp_attempts:a"))
        emailOtps.delete("a", "new")
        assertFalse(redisTemplate.hasKey("email_otp:a"))
        assertEquals("1", redisTemplate.opsForValue().get("email_otp_attempts:a"))
        emailOtps.delete("a", "new")
    }

    companion object {
        @Container
        @ServiceConnection(name = "redis")
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7.4-alpine").withExposedPorts(6379)
    }
}
