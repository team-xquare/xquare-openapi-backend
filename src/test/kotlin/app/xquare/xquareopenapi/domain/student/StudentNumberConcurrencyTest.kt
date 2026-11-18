package app.xquare.xquareopenapi.domain.student

import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentRequest
import app.xquare.xquareopenapi.domain.student.usecase.CreateStudentUseCase
import app.xquare.xquareopenapi.domain.student.usecase.UpdateStudentUseCase
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals

// 스파이 없이 실제로 동시에 요청을 보내, 중복 검사와 unique 제약 중 어디서 걸리든 409 하나로 끝나는지 확인한다.
@SpringBootTest
class StudentNumberConcurrencyTest @Autowired constructor(
    private val createStudentUseCase: CreateStudentUseCase,
    private val updateStudentUseCase: UpdateStudentUseCase,
    private val studentRepository: StudentRepository,
) {

    @AfterEach
    fun tearDown() {
        studentRepository.deleteAll()
    }

    @RepeatedTest(3)
    fun `같은 학번으로 동시에 등록하면 하나만 성공하고 나머지는 STUDENT_NUMBER_ALREADY_EXISTS`() {
        val results = runConcurrently { createStudentUseCase.execute(request(2315)) }

        assertOnlyOneSucceeded(results)
        assertEquals(1, studentRepository.count())
    }

    @RepeatedTest(3)
    fun `여러 학생을 동시에 같은 학번으로 수정하면 하나만 성공하고 나머지는 STUDENT_NUMBER_ALREADY_EXISTS`() {
        val ids = (1..THREADS).map { studentRepository.save(Student(1100 + it, "김준서", LocalDate.of(2009, 7, 28), "s$it@dsm.hs.kr")).id!! }

        val results = runConcurrently { index -> updateStudentUseCase.execute(ids[index], request(2315)) }

        assertOnlyOneSucceeded(results)
        assertEquals(1, studentRepository.findAll().count { it.studentNumber == 2315 })
    }

    private fun runConcurrently(action: (Int) -> Unit): List<ErrorCode?> {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        val results = (0 until THREADS).map { index ->
            executor.submit<ErrorCode?> {
                barrier.await()
                try {
                    action(index)
                    null
                } catch (e: XquareException) {
                    e.errorCode
                }
            }
        }.map { it.get() }
        executor.shutdown()
        return results
    }

    private fun assertOnlyOneSucceeded(results: List<ErrorCode?>) {
        assertEquals(1, results.count { it == null })
        assertEquals(THREADS - 1, results.count { it == ErrorCode.STUDENT_NUMBER_ALREADY_EXISTS })
    }

    private fun request(studentNumber: Int) =
        StudentRequest(studentNumber, "김준서", LocalDate.of(2009, 7, 28), "junseo@dsm.hs.kr")

    companion object {
        private const val THREADS = 4
    }
}
