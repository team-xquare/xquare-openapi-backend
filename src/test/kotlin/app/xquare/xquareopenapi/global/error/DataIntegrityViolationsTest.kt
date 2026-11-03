package app.xquare.xquareopenapi.global.error

import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataIntegrityViolationsTest {

    @Test
    fun `DB가 접두어나 대문자를 붙여 돌려준 제약 이름도 같은 제약으로 본다`() {
        assertTrue(violation("students.uk_students_student_number").violates("uk_students_student_number"))
        assertTrue(violation("PUBLIC.UK_STUDENTS_STUDENT_NUMBER_INDEX_8 ON PUBLIC.STUDENTS(STUDENT_NUMBER)").violates("uk_students_student_number"))
    }

    @Test
    fun `다른 제약이거나 제약 이름을 알 수 없으면 아니라고 본다`() {
        assertFalse(violation("users.uk_users_email").violates("uk_students_student_number"))
        assertFalse(violation(null).violates("uk_students_student_number"))
        assertFalse(DataIntegrityViolationException("원인 없음").violates("uk_students_student_number"))
    }

    private fun violation(constraintName: String?) =
        DataIntegrityViolationException("위반", ConstraintViolationException("위반", SQLException(), constraintName))
}
