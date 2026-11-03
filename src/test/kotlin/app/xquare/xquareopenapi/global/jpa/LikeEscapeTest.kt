package app.xquare.xquareopenapi.global.jpa

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LikeEscapeTest {

    @Test
    fun `%, _, ! 앞에 이스케이프 문자를 붙이고 나머지는 그대로 둔다`() {
        assertEquals("김!%준!_!!", "김%준_!".escapeLike())
        assertEquals("김민준", "김민준".escapeLike())
    }
}
