package app.xquare.xquareopenapi.domain.meal

import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.TimeUnit

// H2가 잠금을 기다리는 세션을 보여 줄 때까지 기다린다. 잠금이 없으면 늦은 쪽이 기다리지 않아 여기서 실패한다.
fun JdbcTemplate.awaitBlockedSession() {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (blockedSessions() == 0) {
        check(System.nanoTime() < deadline) { "잠금을 기다리는 세션이 없습니다." }
        Thread.sleep(10)
    }
}

private fun JdbcTemplate.blockedSessions() =
    queryForObject("select count(*) from information_schema.sessions where blocker_id is not null", Int::class.java)!!
