package app.xquare.xquareopenapi.domain.apikey

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// 키 만료를 시험하려고 Clock 빈을 시각을 바꿀 수 있는 가짜로 바꾼다. 서울 시간대는 ClockConfig와 같다.
class MutableClock(private var instant: Instant, private val zone: ZoneId) : Clock() {
    fun set(dateTime: LocalDateTime) {
        instant = dateTime.atZone(zone).toInstant()
    }

    override fun instant(): Instant = instant
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
}

@TestConfiguration
class MutableClockConfig {
    @Bean
    @Primary
    fun mutableClock() = MutableClock(Instant.EPOCH, ZoneId.of("Asia/Seoul"))
}
