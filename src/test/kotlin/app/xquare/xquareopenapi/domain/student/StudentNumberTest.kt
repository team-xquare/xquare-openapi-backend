package app.xquare.xquareopenapi.domain.student

import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StudentNumberTest {

    @Test
    fun `학번은 1000부터 3999까지 받는다`() {
        assertEquals(1000, student(1000).studentNumber)
        assertEquals(3999, student(3999).studentNumber)

        val student = student(2315)
        student.update(1000, "김준서", BIRTH_DATE, EMAIL)
        assertEquals(1000, student.studentNumber)
        student.update(3999, "김준서", BIRTH_DATE, EMAIL)
        assertEquals(3999, student.studentNumber)
    }

    @Test
    fun `학번이 1000~3999를 벗어나면 INVALID_STUDENT_NUMBER`() {
        listOf(999, 4000, 0, -2315).forEach { studentNumber ->
            assertInvalid { student(studentNumber) }
            assertInvalid { student(2315).update(studentNumber, "김준서", BIRTH_DATE, EMAIL) }
        }
    }

    private fun assertInvalid(block: () -> Unit) {
        val e = assertFailsWith<XquareException> { block() }
        assertEquals(ErrorCode.INVALID_STUDENT_NUMBER, e.errorCode)
    }

    private fun student(studentNumber: Int) = Student(studentNumber, "김준서", BIRTH_DATE, EMAIL)

    companion object {
        private val BIRTH_DATE = LocalDate.of(2009, 7, 28)
        private const val EMAIL = "junseo@dsm.hs.kr"
    }
}
