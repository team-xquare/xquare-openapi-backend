package app.xquare.xquareopenapi.domain.studysupervisor

import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorAssignmentRequest
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorRequest
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.DeleteStudySupervisorUseCase
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.UpdateStudySupervisorUseCase
import app.xquare.xquareopenapi.domain.teacher.Teacher
import app.xquare.xquareopenapi.domain.teacher.TeacherPosition
import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.domain.teacher.TeacherSubject
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@SpringBootTest
class StudySupervisorConcurrencyTest @Autowired constructor(
    private val updateStudySupervisorUseCase: UpdateStudySupervisorUseCase,
    private val deleteStudySupervisorUseCase: DeleteStudySupervisorUseCase,
    private val studySupervisorRepository: StudySupervisorRepository,
    private val assignmentRepository: StudySupervisorAssignmentRepository,
    private val teacherRepository: TeacherRepository,
) {

    @AfterEach
    fun tearDown() {
        assignmentRepository.deleteAll()
        studySupervisorRepository.deleteAll()
        teacherRepository.deleteAll()
    }

    // 조회 후 삭제하면 두 요청이 모두 조회를 통과했을 때 늦은 쪽이 커밋하며 500이 난다.
    @RepeatedTest(3)
    fun `같은 정보를 동시에 지우면 하나만 성공하고 나머지는 SUPERVISION_NOT_FOUND`() {
        val id = studySupervisorRepository.save(StudySupervisor(LocalDate.of(2026, 9, 14))).id!!

        val results = concurrently { deleteStudySupervisorUseCase.execute(id) }

        assertEquals(1, results.count { it == null })
        assertEquals(THREADS - 1, results.count { it == ErrorCode.SUPERVISION_NOT_FOUND })
        assertFalse(studySupervisorRepository.existsById(id))
    }

    // 행을 잠그지 않으면 배정 교체가 섞여 같은 층이 두 번 들어가거나 unique 제약에 걸려 500이 난다.
    @RepeatedTest(3)
    fun `같은 정보를 동시에 수정하면 모두 성공하고 한 요청의 배정만 섞이지 않고 남는다`() {
        val id = studySupervisorRepository.save(StudySupervisor(LocalDate.of(2026, 9, 14))).id!!
        val teachers = (1..THREADS).map {
            teacherRepository.save(Teacher("선생$it", null, null, TeacherSubject.MATH, "3층 교무실", false, TeacherPosition.MEISTER))
        }
        val requests = teachers.map { teacher ->
            StudySupervisorRequest(
                LocalDate.of(2026, 9, 14),
                listOf(StudySupervisorAssignmentRequest(2, teacher.id!!), StudySupervisorAssignmentRequest(3, teacher.id!!)),
            )
        }
        val next = AtomicInteger()

        val results = concurrently { updateStudySupervisorUseCase.execute(id, requests[next.getAndIncrement()]) }

        assertEquals(List(THREADS) { null }, results)
        val assignments = assignmentRepository.findAll()
        assertEquals(listOf(2, 3), assignments.map { it.floor }.sorted())
        assertEquals(1, assignments.map { it.teacher.id }.toSet().size)
    }

    private fun concurrently(action: () -> Unit): List<ErrorCode?> {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        val results = (1..THREADS).map {
            executor.submit(
                Callable<ErrorCode?> {
                    barrier.await()
                    try {
                        action()
                        null
                    } catch (e: XquareException) {
                        e.errorCode
                    }
                },
            )
        }.map { it.get() }
        executor.shutdown()
        return results
    }

    companion object {
        private const val THREADS = 4
    }
}
