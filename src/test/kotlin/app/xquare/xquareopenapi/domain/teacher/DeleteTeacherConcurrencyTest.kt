package app.xquare.xquareopenapi.domain.teacher

import app.xquare.xquareopenapi.domain.teacher.usecase.DeleteTeacherUseCase
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// 조회 후 삭제하던 때는 두 요청이 모두 조회를 통과하면 늦은 쪽이 커밋하며 500이 났다.
@SpringBootTest
class DeleteTeacherConcurrencyTest @Autowired constructor(
    private val deleteTeacherUseCase: DeleteTeacherUseCase,
    private val teacherRepository: TeacherRepository,
) {

    @RepeatedTest(3)
    fun `같은 교직원을 동시에 지우면 하나만 성공하고 나머지는 TEACHER_NOT_FOUND`() {
        val id = teacherRepository.save(Teacher("홍길동", 1, 2, TeacherSubject.MATH, "3층 교무실", true, TeacherPosition.ACADEMIC_AFFAIRS)).id!!
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)

        val results = (1..THREADS).map {
            executor.submit<ErrorCode?> {
                barrier.await()
                try {
                    deleteTeacherUseCase.execute(id)
                    null
                } catch (e: XquareException) {
                    e.errorCode
                }
            }
        }.map { it.get() }
        executor.shutdown()

        assertEquals(1, results.count { it == null })
        assertEquals(THREADS - 1, results.count { it == ErrorCode.TEACHER_NOT_FOUND })
        assertFalse(teacherRepository.existsById(id))
    }

    companion object {
        private const val THREADS = 4
    }
}
