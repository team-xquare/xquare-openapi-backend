package app.xquare.xquareopenapi.domain.student

import app.xquare.xquareopenapi.domain.student.usecase.DeleteStudentUseCase
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// 조회 후 삭제하던 때는 두 요청이 모두 조회를 통과하면 늦은 쪽이 커밋하며 500이 났다.
@SpringBootTest
class DeleteStudentConcurrencyTest @Autowired constructor(
    private val deleteStudentUseCase: DeleteStudentUseCase,
    private val studentRepository: StudentRepository,
) {

    @RepeatedTest(3)
    fun `같은 학생을 동시에 지우면 하나만 성공하고 나머지는 STUDENT_NOT_FOUND`() {
        val id = studentRepository.save(Student(2315, "김준서", LocalDate.of(2009, 7, 28), "s@dsm.hs.kr")).id!!
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)

        val results = (1..THREADS).map {
            executor.submit<ErrorCode?> {
                barrier.await()
                try {
                    deleteStudentUseCase.execute(id)
                    null
                } catch (e: XquareException) {
                    e.errorCode
                }
            }
        }.map { it.get() }
        executor.shutdown()

        assertEquals(1, results.count { it == null })
        assertEquals(THREADS - 1, results.count { it == ErrorCode.STUDENT_NOT_FOUND })
        assertFalse(studentRepository.existsById(id))
    }

    companion object {
        private const val THREADS = 4
    }
}
