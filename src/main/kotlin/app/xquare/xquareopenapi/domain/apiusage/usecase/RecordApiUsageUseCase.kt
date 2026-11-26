package app.xquare.xquareopenapi.domain.apiusage.usecase

import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLog
import com.zaxxer.hikari.HikariDataSource
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Types
import java.time.temporal.ChronoUnit

class RecordApiUsageUseCase(private val pool: HikariDataSource) : AutoCloseable {
    private val jdbc = JdbcTemplate(pool)

    // 단일 INSERT를 전용 풀에서 자동 커밋한다. 업무용 JPA 트랜잭션에는 참여하지 않는다.
    fun execute(log: ApiUsageLog) {
        jdbc.update(
            "insert into api_usage_logs (occurred_at, http_method, route, http_status, duration_ms, " +
                "project_id, user_id, key_prefix, error_code, access_level) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ) { statement ->
            statement.setObject(1, log.occurredAt.truncatedTo(ChronoUnit.MICROS))
            statement.setString(2, log.method)
            statement.setString(3, log.route)
            statement.setInt(4, log.status)
            statement.setLong(5, log.durationMs)
            statement.setObject(6, log.projectId, Types.BIGINT)
            statement.setObject(7, log.userId, Types.BIGINT)
            statement.setObject(8, log.keyPrefix, Types.VARCHAR)
            statement.setObject(9, log.errorCode, Types.VARCHAR)
            statement.setObject(10, log.accessLevel, Types.VARCHAR)
        }
    }

    override fun close() = pool.close()
}
