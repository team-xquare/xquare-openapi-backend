package app.xquare.xquareopenapi.domain.auth.repository

import app.xquare.xquareopenapi.global.otp.OtpProperties
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// Lua 스크립트 자체는 RedisAuthRepositoriesTest(Docker 필요)에서 확인한다.
// 여기서는 Docker 없이 스크립트에 넘기는 키·인자와 결과 해석을 확인한다.
class RedisEmailOtpRepositoryTest {
    private val properties = OtpProperties(Duration.ofSeconds(60), Duration.ofHours(1), 5, 30, Duration.ofMinutes(30), 4)
    private val calls = mutableListOf<List<Any?>>()

    private fun repository(result: Long?, ttlMillis: Long = 0): RedisEmailOtpRepository {
        val redis = mock(StringRedisTemplate::class.java) { invocation ->
            calls.add(listOf(invocation.method.name) + invocation.arguments.toList())
            when (invocation.method.name) {
                "execute" -> result
                "getExpire" -> ttlMillis
                else -> null
            }
        }
        return RedisEmailOtpRepository(redis, properties)
    }

    @Test
    fun `발송 스크립트에 계약된 키와 제한 값을 넘기고 0이면 저장한 것이다`() {
        assertNull(repository(0L).save("a@dsm.hs.kr", "10.0.0.1", "123456", Duration.ofSeconds(300)))
        val call = calls.single()
        assertEquals("execute", call[0])
        assertEquals(
            listOf(
                "email_otp:a@dsm.hs.kr",
                "email_otp_attempts:a@dsm.hs.kr",
                "email_otp_cooldown:a@dsm.hs.kr",
                "email_otp_send_count:a@dsm.hs.kr",
                "email_otp_ip_send_count:10.0.0.1",
            ),
            call[2],
        )
        assertEquals(listOf("123456", "300000", "60000", "3600000", "5", "30", "4"), call.drop(3))
    }

    @Test
    fun `발송 스크립트 결과마다 걸린 제한과 그 키의 남은 시간을 돌려준다`() {
        val cases = listOf(
            Triple(1L, OtpSendRejection.ATTEMPTS_EXCEEDED, "email_otp_attempts:a"),
            Triple(2L, OtpSendRejection.EMAIL_LIMIT, "email_otp_send_count:a"),
            Triple(3L, OtpSendRejection.IP_LIMIT, "email_otp_ip_send_count:ip"),
            Triple(4L, OtpSendRejection.COOLDOWN, "email_otp_cooldown:a"),
        )
        for ((result, reason, key) in cases) {
            calls.clear()
            val limit = repository(result, 1500).save("a", "ip", "123456", Duration.ofSeconds(300))
            assertEquals(OtpSendLimit(reason, Duration.ofMillis(1500)), limit)
            assertEquals(listOf("getExpire", key, TimeUnit.MILLISECONDS), calls.last())
        }
    }

    @Test
    fun `남은 시간을 읽기 전에 키가 만료됐으면 1ms로 돌려준다`() {
        for ((ttl, expected) in listOf(-2L to 1L, 0L to 1L, 1L to 1L, 2L to 2L)) {
            val limit = repository(4L, ttl).save("a", "ip", "123456", Duration.ofSeconds(300))
            assertEquals(Duration.ofMillis(expected), limit?.retryAfter)
        }
    }

    @Test
    fun `스크립트가 예상 밖 결과를 주면 성공으로 처리하지 않는다`() {
        for (result in listOf(5L, -1L, null)) {
            val saveError = assertFailsWith<IllegalStateException> {
                repository(result).save("example@dsm.hs.kr", "ip", "123456", Duration.ofSeconds(300))
            }
            assertEquals("OTP 스크립트 실행 실패", saveError.message)
        }
        for (result in listOf(3L, -1L, null)) {
            val error = assertFailsWith<IllegalStateException> {
                repository(result).consume("example@dsm.hs.kr", "123456")
            }
            assertEquals("OTP 스크립트 실행 실패", error.message)
        }
    }

    @Test
    fun `검증 스크립트에 횟수 상한과 창 길이를 넘기고 결과를 구분한다`() {
        val expected = mapOf(0L to OtpVerification.NOT_FOUND, 1L to OtpVerification.MATCHED, 2L to OtpVerification.MISMATCH)
        for ((result, verification) in expected) {
            calls.clear()
            assertEquals(verification, repository(result).consume("a", "123456"))
            val call = calls.single()
            assertEquals(listOf("email_otp:a", "email_otp_attempts:a"), call[2])
            assertEquals(listOf("123456", "4", "1800000"), call.drop(3))
        }
    }

    @Test
    fun `발송 실패 정리는 OTP 키만 넘기고 횟수 키는 건드리지 않는다`() {
        repository(1L).delete("a", "123456")
        val call = calls.single()
        assertEquals(listOf("email_otp:a"), call[2])
        assertEquals(listOf("123456"), call.drop(3))
    }
}
