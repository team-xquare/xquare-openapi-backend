package app.xquare.xquareopenapi.domain.apikey

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer

// 운영 DB와 같은 MySQL 8.4(REPEATABLE READ)에서 관리자 동시 처리 시나리오를 돌린다.
// 잠글 프로젝트 id를 트랜잭션 밖에서 읽어, 잠금을 기다린 뒤의 조회가 다른 관리자가 처리한 상태를 보는지 확인한다.
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DecideApiApplicationMySqlConcurrencyTest : DecideApiApplicationConcurrencyScenarios() {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
