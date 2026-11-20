package app.xquare.xquareopenapi.config

import app.xquare.xquareopenapi.domain.apiusage.usecase.RecordApiUsageUseCase
import app.xquare.xquareopenapi.global.apiusage.ApiUsageLogCapture
import app.xquare.xquareopenapi.global.apiusage.ApiUsageLogWriter
import com.zaxxer.hikari.HikariDataSource
import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import java.time.Clock

@Configuration
class ApiUsageLogConfig {
    @Bean
    fun recordApiUsageUseCase(details: JdbcConnectionDetails): RecordApiUsageUseCase {
        // 데이터소스 빈으로 등록하면 Boot의 업무용 풀 자동 구성이 바뀔 수 있다.
        // 기록 전용 풀은 기록 빈이 소유하고, 첫 기록까지 연결을 열지 않는다.
        val pool = HikariDataSource().apply {
            poolName = "api-usage-log-pool"
            jdbcUrl = details.jdbcUrl
            username = details.username
            password = details.password
            driverClassName = details.driverClassName
            maximumPoolSize = 1
            minimumIdle = 0
            connectionTimeout = 1_000
            validationTimeout = 500
            initializationFailTimeout = -1
            if (jdbcUrl.startsWith("jdbc:mysql:")) {
                addDataSourceProperty("connectTimeout", 2_000)
                addDataSourceProperty("socketTimeout", 2_000)
            }
        }
        return RecordApiUsageUseCase(pool)
    }

    @Bean
    fun apiUsageLogWriter(
        recorder: RecordApiUsageUseCase,
        @Value("\${api-usage-log.queue-capacity}") queueCapacity: Int,
    ): ApiUsageLogWriter {
        require(queueCapacity in 1..4096) { "api-usage-log.queue-capacity는 1~4096이어야 합니다." }
        return ApiUsageLogWriter(recorder, queueCapacity)
    }

    @Bean
    fun apiUsageLogCapture(clock: Clock, writer: ApiUsageLogWriter): ApiUsageLogCapture =
        ApiUsageLogCapture(clock, writer)

    @Bean
    fun apiUsageLogFilter(capture: ApiUsageLogCapture): FilterRegistrationBean<ApiUsageLogCapture> =
        FilterRegistrationBean(capture).apply {
            // 인증·인가에서 막힌 요청도 관찰하되, Spring Security 체인에는 관여하지 않는다.
            order = Ordered.HIGHEST_PRECEDENCE + 20
            setDispatcherTypes(DispatcherType.REQUEST)
        }

    @Bean
    fun apiUsageLogListener(capture: ApiUsageLogCapture): ServletListenerRegistrationBean<ApiUsageLogCapture> =
        ServletListenerRegistrationBean(capture)
}
