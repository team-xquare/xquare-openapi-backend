package app.xquare.xquareopenapi.domain.apikey

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ApiProjectTest {

    @Test
    fun `키를 발급한 프로젝트는 갱신하면 키는 그대로 두고 만료 시각만 지금부터 60일 뒤로 늦춘다`() {
        val project = ApiProject(1L, "급식 알리미", NOW)
        project.issueKey(NOW)
        val keyHash = project.keyHash

        project.renewKey(LATER)

        assertEquals(keyHash, project.keyHash)
        assertEquals(NOW, project.issuedAt)
        assertEquals(LATER.plusDays(60), project.expiresAt)
    }

    @Test
    fun `키가 발급되지 않은 프로젝트를 갱신하면 IllegalStateException이고 만료 시각은 그대로 null이다`() {
        val project = ApiProject(1L, "급식 알리미", NOW)

        assertFailsWith<IllegalStateException> { project.renewKey(LATER) }
        assertNull(project.expiresAt)
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 24, 10, 0)
        private val LATER = LocalDateTime.of(2026, 12, 1, 10, 0)
    }
}
