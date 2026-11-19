package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer

// 운영 DB와 같은 MySQL 8.4(기본 격리 수준 REPEATABLE READ)에서 동시 신청 시나리오를 돌린다.
// H2는 커밋된 행을 바로 보여 줘서, 옛 스냅샷 때문에 중복이 unique 제약까지 가는 경우를 재현하지 못한다.
@SpringBootTest
@Import(FakeAuthRepositories::class)
@Testcontainers(disabledWithoutDocker = true)
class ApplyApiKeyMySqlConcurrencyTest : ApplyApiKeyConcurrencyScenarios() {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
