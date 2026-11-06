package app.xquare.xquareopenapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

// "이번 달" 같은 날짜 계산은 서버 시간대와 상관없이 서울 시간 기준으로 한다.
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
