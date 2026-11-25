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

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
