package app.xquare.xquareopenapi.domain.student

import app.xquare.xquareopenapi.global.error.violates
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// 검색 쿼리(mod, 파라미터 산술)와 제약 이름이 운영 DB인 MySQL에서도 같게 동작하는지 확인한다. H2는 StudentTest가 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class StudentRepositoryMySqlTest @Autowired constructor(
    private val studentRepository: StudentRepository,
) {

    @Test
    fun `학번이 겹치면 학번 제약 위반으로 구분한다`() {
        studentRepository.saveAndFlush(student(2315))

        val e = assertFailsWith<DataIntegrityViolationException> {
            studentRepository.saveAndFlush(student(2315))
        }

        assertTrue(e.violates(Student.STUDENT_NUMBER_UNIQUE_CONSTRAINT))
    }

    @Test
    fun `이름 일부, 학년, 반으로 찾는다`() {
        listOf(1999 to "김민준", 2000 to "이서준", 2101 to "김민서", 2199 to "박민준", 2200 to "최민준", 3101 to "김민준")
            .forEach { (studentNumber, name) -> studentRepository.save(student(studentNumber, name)) }

        assertEquals(listOf(2000, 2101, 2199, 2200), search(grade = 2))
        assertEquals(listOf(2101, 2199, 3101), search(classNum = 1))
        assertEquals(listOf(2101, 2199), search(grade = 2, classNum = 1))
        assertEquals(listOf(1999, 2199, 2200, 3101), search(name = "민준"))
        assertEquals(listOf(2199), search(name = "민준", grade = 2, classNum = 1))
        assertEquals(6, search().size)
    }

    private fun search(name: String? = null, grade: Int? = null, classNum: Int? = null) =
        studentRepository.search(name, grade, classNum, PageRequest.of(0, 20, Sort.by("studentNumber")))
            .content.map { it.studentNumber }

    private fun student(studentNumber: Int, name: String = "김준서") =
        Student(studentNumber, name, LocalDate.of(2009, 7, 28), "s$studentNumber@dsm.hs.kr")

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
