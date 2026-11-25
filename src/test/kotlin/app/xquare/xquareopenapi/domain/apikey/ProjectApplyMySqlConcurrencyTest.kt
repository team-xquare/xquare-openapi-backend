package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer

// 운영 DB와 같은 MySQL 8.4(기본 격리 수준 REPEATABLE READ)에서 동시 신청 시나리오를 돌린다.
// 잠금을 트랜잭션의 첫 조회로 둬서, 잠금을 기다린 뒤의 일반 조회가 앞 요청이 커밋한 행을 보는지 확인한다.
@SpringBootTest
@Import(FakeAuthRepositories::class)
@Testcontainers(disabledWithoutDocker = true)
class ProjectApplyMySqlConcurrencyTest : ProjectApplyConcurrencyScenarios() {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
