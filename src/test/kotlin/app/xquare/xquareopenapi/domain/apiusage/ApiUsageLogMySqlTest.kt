package app.xquare.xquareopenapi.domain.apiusage

import app.xquare.xquareopenapi.domain.apiusage.presentation.ApiUsageLogQuery
import app.xquare.xquareopenapi.domain.apiusage.usecase.GetApiUsageLogsUseCase
import app.xquare.xquareopenapi.domain.apiusage.usecase.RecordApiUsageUseCase
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.core.io.FileSystemResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.transaction.PlatformTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=none"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ApiUsageLogMySqlTest @Autowired constructor(
    private val dataSource: DataSource,
    private val repository: ApiUsageLogRepository,
    private val entityManager: EntityManager,
    private val transactionManager: PlatformTransactionManager,
) {
    @Test
    fun `MySQL 배포 DDL에 메타데이터를 마이크로초 정밀도로 저장하고 읽는다`() {
        JdbcTemplate(dataSource).execute("drop table if exists api_usage_logs")
        DatabasePopulatorUtils.execute(ResourceDatabasePopulator(FileSystemResource("docs/schema/api_usage_logs.sql")), dataSource)
        val time = LocalDateTime.of(2026, 11, 20, 11, 12, 13, 123456000)
        val pool = HikariDataSource().apply {
            jdbcUrl = mysql.jdbcUrl
            username = mysql.username
            password = mysql.password
            maximumPoolSize = 1
        }
        RecordApiUsageUseCase(pool).use { recorder ->
            recorder.execute(ApiUsageLog(time.plusNanos(900), "POST", "/auth/signup", 415, 7))
            recorder.execute(ApiUsageLog(time.plusNanos(1000), "GET", "/meals", 200, 1))
            val fixtureTime = time.plusSeconds(1)
            listOf("/fixture/a%b", "/fixture/a_b", "/fixture/a!b", "/fixture/axb", "/fixture/ab", "UNMAPPED").forEach { route ->
                recorder.execute(ApiUsageLog(fixtureTime, "GET", route, 200, 1))
            }
            listOf("/students", "/students/{studentId}", "/STUDENTS", "/stúdents", "/students/", "/students/123").forEach { route ->
                recorder.execute(ApiUsageLog(fixtureTime, "GET", route, 404, 1))
            }
        }
        entityManager.clear()
        val row = repository.findAll().single { it.status == 415 }
        assertNotNull(row.id)
        assertEquals(time, row.occurredAt)
        assertEquals(listOf("POST", "/auth/signup", 415, 7L), listOf(row.method, row.route, row.status, row.durationMs))

        val query = GetApiUsageLogsUseCase(repository, Clock.systemUTC(), transactionManager)
        val at = time.toInstant(ZoneOffset.UTC)
        // 경계가 저장값과 같을 때와 마이크로초 사이일 때 모두 [from, to)를 지킨다.
        assertEquals(listOf(415), query.execute(ApiUsageLogQuery(at, at.plusNanos(100))).content.map { it.status })
        assertEquals(emptyList(), query.execute(ApiUsageLogQuery(at.plusNanos(100), at.plusNanos(1000))).content)
        assertEquals(listOf(200), query.execute(ApiUsageLogQuery(at.plusNanos(100), at.plusNanos(1100))).content.map { it.status })

        val searchStart = at.plusSeconds(1)
        val searchEnd = at.plusSeconds(2)
        for (term in listOf("a%b", "a_b", "a!b")) {
            val result = query.execute(ApiUsageLogQuery(searchStart, searchEnd, api = term))
            assertEquals(listOf("/fixture/$term"), result.content.map { it.route })
        }
        assertEquals(0L, query.execute(ApiUsageLogQuery(searchStart, searchEnd, api = "UNMAPPED")).totalCount)
        val firstPage = query.execute(ApiUsageLogQuery(searchStart, searchEnd, status = 404, size = 1, field = "STUDENT"))
        assertEquals(2L, firstPage.totalCount)
        assertEquals(listOf("/students/{studentId}"), firstPage.content.map { it.route })
    }

    @Test
    fun `기존 테이블에 변경 스크립트를 적용하면 상세 항목을 저장하고 조회한다`() {
        val jdbc = JdbcTemplate(dataSource)
        jdbc.execute("drop table if exists api_usage_logs")
        // 이 PR 이전의 배포 DDL
        jdbc.execute(
            """
            create table api_usage_logs (
                id bigint not null auto_increment, occurred_at datetime(6) not null, http_method varchar(8) not null,
                route varchar(160) not null, http_status int not null, duration_ms bigint not null,
                primary key (id), key ix_api_usage_logs_occurred_id (occurred_at, id)
            ) engine = InnoDB default charset = utf8mb4
            """.trimIndent(),
        )
        val time = LocalDateTime.of(2026, 11, 20, 11, 0)
        jdbc.update(
            "insert into api_usage_logs (occurred_at, http_method, route, http_status, duration_ms) values (?, 'GET', '/meals', 200, 1)", time,
        )
        DatabasePopulatorUtils.execute(
            ResourceDatabasePopulator(FileSystemResource("docs/schema/migrations/2026-11-28-api-usage-logs-detail.sql")), dataSource,
        )
        val pool = HikariDataSource().apply {
            jdbcUrl = mysql.jdbcUrl
            username = mysql.username
            password = mysql.password
            maximumPoolSize = 1
        }
        RecordApiUsageUseCase(pool).use { recorder ->
            recorder.execute(
                ApiUsageLog(
                    time.plusSeconds(1), "GET", "UNMAPPED", 403, 2, projectId = 5L, userId = 50L, keyPrefix = "xq_ab12cd34",
                    errorCode = "API_KEY_FIELD_FORBIDDEN", accessLevel = "RESTRICTED",
                ),
            )
        }
        entityManager.clear()
        val rows = repository.findAll().sortedBy { it.id }
        assertEquals(listOf(null, null, null, null, null), rows[0].let { listOf(it.projectId, it.userId, it.keyPrefix, it.errorCode, it.accessLevel) })
        assertEquals(
            listOf(5L, 50L, "xq_ab12cd34", "API_KEY_FIELD_FORBIDDEN", "RESTRICTED"),
            rows[1].let { listOf(it.projectId, it.userId, it.keyPrefix, it.errorCode, it.accessLevel) },
        )
        val query = GetApiUsageLogsUseCase(repository, Clock.systemUTC(), transactionManager)
        val at = time.toInstant(ZoneOffset.UTC)
        val filtered = query.execute(ApiUsageLogQuery(at, at.plusSeconds(5), level = "RESTRICTED", projectId = 5L, userId = 50L, errorCode = "API_KEY_FIELD_FORBIDDEN"))
        assertEquals(listOf(403), filtered.content.map { it.status })
        // 변경 스크립트와 신규 DDL의 열·인덱스가 같아야 신규·기존 DB가 같은 스키마가 된다.
        val indexes = jdbc.queryForList(
            "select index_name, column_name from information_schema.statistics where table_schema = database() and table_name = 'api_usage_logs' order by index_name, seq_in_index",
        ).map { "${it["INDEX_NAME"]}.${it["COLUMN_NAME"]}" }
        assertEquals(
            listOf("PRIMARY.id", "ix_api_usage_logs_occurred_id.occurred_at", "ix_api_usage_logs_occurred_id.id",
                "ix_api_usage_logs_project_occurred.project_id", "ix_api_usage_logs_project_occurred.occurred_at").sorted(),
            indexes.sorted(),
        )
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
