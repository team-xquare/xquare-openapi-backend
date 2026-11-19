package app.xquare.xquareopenapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

// 나이스 급식 동기화(NeisMealSyncScheduler)를 위해 스케줄링을 켜고 나이스 API용 RestClient를 만든다.
@Configuration
@EnableScheduling
class NeisConfig {

    // 기본 요청 팩토리는 읽기 제한 시간이 없어 나이스가 응답하지 않으면 스케줄러 스레드가 멈춘다.
    @Bean
    fun neisRestClient(): RestClient {
        val httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(READ_TIMEOUT) }
        return RestClient.builder()
            .baseUrl(BASE_URL)
            .requestFactory(requestFactory)
            .build()
    }

    companion object {
        private const val BASE_URL = "https://open.neis.go.kr"
        private val CONNECT_TIMEOUT = Duration.ofSeconds(5)
        private val READ_TIMEOUT = Duration.ofSeconds(10)
    }
}
