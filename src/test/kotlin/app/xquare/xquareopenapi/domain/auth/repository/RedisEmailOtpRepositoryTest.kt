package app.xquare.xquareopenapi.domain.auth.repository

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.doReturn
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RedisEmailOtpRepositoryTest {
    @Test
    fun `스크립트가 예상 밖 결과를 주면 성공으로 처리하지 않는다`() {
        for (result in listOf(3L, null)) {
            val redis = mock(StringRedisTemplate::class.java)
            doReturn(result).`when`(redis).execute(any<RedisScript<Long>>(), anyList(), anyString())
            val error = assertFailsWith<IllegalStateException> {
                RedisEmailOtpRepository(redis).consume("example@dsm.hs.kr", "123456")
            }
            assertEquals("OTP 스크립트 실행 실패", error.message)
        }
    }
}
