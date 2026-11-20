package app.xquare.xquareopenapi.domain.apiusage

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.core.io.FileSystemResource
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer
import java.time.LocalDateTime
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
) {
    @Test
    fun `MySQL 배포 DDL에 메타데이터를 마이크로초 정밀도로 저장하고 읽는다`() {
        DatabasePopulatorUtils.execute(ResourceDatabasePopulator(FileSystemResource("docs/schema/api_usage_logs.sql")), dataSource)
        val time = LocalDateTime.of(2026, 11, 20, 11, 12, 13, 123456000)
        val id = assertNotNull(repository.saveAndFlush(ApiUsageLog(time, "POST", "/auth/signup", 415, 7)).id)
        entityManager.clear()
        val row = repository.findById(id).orElseThrow()
        assertEquals(time, row.occurredAt)
        assertEquals(listOf("POST", "/auth/signup", 415, 7L), listOf(row.method, row.route, row.status, row.durationMs))
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
