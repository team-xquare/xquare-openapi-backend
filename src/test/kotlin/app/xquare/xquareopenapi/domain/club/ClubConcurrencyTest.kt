package app.xquare.xquareopenapi.domain.club

import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubRequest
import app.xquare.xquareopenapi.domain.club.usecase.CreateClubUseCase
import app.xquare.xquareopenapi.domain.club.usecase.UpdateClubUseCase
import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.domain.teacher.Teacher
import app.xquare.xquareopenapi.domain.teacher.TeacherPosition
import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.domain.teacher.TeacherSubject
import app.xquare.xquareopenapi.domain.teacher.usecase.DeleteTeacherUseCase
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mockingDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 학생 행 잠금을 실제 스레드 여러 개로 겹쳐 본다. ClubTest는 스파이로 중복 검사만 통과시켜 unique 제약을 확인한다.
@SpringBootTest
class ClubConcurrencyTest @Autowired constructor(
    private val createClubUseCase: CreateClubUseCase,
    private val updateClubUseCase: UpdateClubUseCase,
    private val clubRepository: ClubRepository,
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository,
    private val deleteTeacherUseCase: DeleteTeacherUseCase,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
) {
    // 동아리 등록이 담당 교직원을 잠근 직후 멈춰 세우는 데 쓴다.
    // 저장소는 JDK 프록시라 진짜 스파이가 아니라 실제 빈에 위임하는 목이다. 실제 동작은 callRealMethod 대신 기본 응답(위임)으로 부른다.
    @MockitoSpyBean
    private lateinit var spiedTeacherRepository: TeacherRepository

    @AfterEach
    fun tearDown() {
        clubRepository.deleteAll()
        studentRepository.deleteAll()
        teacherRepository.deleteAll()
    }

    // 같은 학생이 두 동아리에 들어가는 것은 DB 제약 없이 학생 행 잠금으로만 막는다. 잠금을 빼면 동아리가 여러 개 생긴다.
    @RepeatedTest(3)
    fun `같은 학생을 동시에 여러 동아리에 넣으면 하나만 등록되고 나머지는 STUDENT_ALREADY_IN_CLUB`() {
        val studentId = student(2315).id!!
        val rooms = listOf(ClubRoom.ROOM_2_1, ClubRoom.ROOM_2_2, ClubRoom.ROOM_2_3, ClubRoom.ROOM_2_4)
        val barrier = CyclicBarrier(rooms.size)
        val executor = Executors.newFixedThreadPool(rooms.size)

        val results = rooms.map { room ->
            executor.submit<ErrorCode?> {
                barrier.await()
                try {
                    createClubUseCase.execute(ClubRequest("DMS", "logo.png", room, studentId, listOf(studentId)))
                    null
                } catch (e: XquareException) {
                    e.errorCode
                }
            }
        }.map { it.get() }
        executor.shutdown()

        assertEquals(1, results.count { it == null })
        assertEquals(rooms.size - 1, results.count { it == ErrorCode.STUDENT_ALREADY_IN_CLUB })
        val club = clubRepository.findAll().single()
        assertEquals(listOf(studentId), studentRepository.findAllByClubIdIn(listOf(club.id!!)).map { it.id })
    }

    // 빠지는 기존 구성원을 새 구성원보다 늦게 잠그던 때는 데드락이 났다(#26 리뷰).
    // 수정은 q를 잠근 뒤 p를 기다리고, 다른 요청은 p를 잡은 채 q를 기다려 DB가 한쪽을 되돌렸다.
    @Test
    fun `구성원을 빼는 수정이 다른 요청의 학생 잠금과 겹쳐도 데드락 없이 둘 다 끝난다`() {
        val p = student(2101)
        val q = student(2102)
        val club = clubRepository.save(Club("DMS", "logo.png", ClubRoom.ROOM_2_1, p, null))
        p.joinClub(club)
        q.joinClub(club)
        studentRepository.saveAll(listOf(p, q))
        val pLocked = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        // 다른 동아리 등록·수정처럼 학생 행을 id 순으로 잠근다. p를 잡고, 수정이 잠금을 기다리기 시작하면 q를 요청한다.
        val other = executor.submit {
            transactionTemplate.executeWithoutResult {
                studentRepository.findAllByIdInForUpdate(listOf(p.id!!))
                pLocked.countDown()
                awaitBlockedSession()
                studentRepository.findAllByIdInForUpdate(listOf(q.id!!))
            }
        }
        pLocked.await()
        val update = runCatching {
            updateClubUseCase.execute(club.id!!, ClubRequest("DMS", "logo.png", ClubRoom.ROOM_2_1, q.id!!, listOf(q.id!!)))
        }
        val otherResult = runCatching { other.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertNull(update.exceptionOrNull())
        assertNull(otherResult.exceptionOrNull())
        assertEquals(listOf(q.id), studentRepository.findAllByClubIdIn(listOf(club.id!!)).map { it.id })
    }

    // 교직원을 잠그지 않고 읽던 때는, 읽은 뒤 교직원 삭제가 먼저 커밋되면 동아리 저장이 외래 키에 걸려 형식 없는 500이 났다(#26 리뷰).
    @RepeatedTest(3)
    fun `동아리 등록이 담당 교직원을 읽은 뒤 그 교직원을 지우면 삭제가 등록 커밋을 기다렸다가 지운다`() {
        val manager = student(2315)
        val teacher = teacherRepository.save(Teacher("이진경", null, null, TeacherSubject.MATH, "3층 교무실", false, TeacherPosition.MEISTER))
        val teacherLocked = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        doAnswer { invocation ->
            val found = mockingDetails(spiedTeacherRepository).mockCreationSettings.defaultAnswer.answer(invocation)
            teacherLocked.countDown()
            // 교직원 삭제가 이 잠금을 기다리기 시작할 때까지 멈춘다. 잠그지 않으면 삭제가 기다리지 않아 여기서 실패한다.
            awaitBlockedSession()
            found
        }.`when`(spiedTeacherRepository).findByIdForUpdate(anyLong())

        val create = executor.submit<Long> {
            createClubUseCase.execute(ClubRequest("DMS", "logo.png", ClubRoom.ROOM_2_1, manager.id!!, listOf(manager.id!!), teacher.id))
        }
        assertTrue(teacherLocked.await(5, TimeUnit.SECONDS))
        val delete = runCatching { deleteTeacherUseCase.execute(teacher.id!!) }
        val created = runCatching { create.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertNull(created.exceptionOrNull())
        assertNull(delete.exceptionOrNull())
        // 등록이 먼저 커밋됐고, 삭제가 그 뒤에 지우면서 담당 교직원을 null로 바꿨다(ON DELETE SET NULL).
        val club = clubRepository.findById(created.getOrThrow()).get()
        assertNull(club.teacher)
        assertEquals(0, teacherRepository.count())
    }

    // H2가 잠금을 기다리는 세션을 보여 줄 때까지 기다린다.
    private fun awaitBlockedSession() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (blockedSessions() == 0) {
            check(System.nanoTime() < deadline) { "잠금을 기다리는 세션이 없습니다." }
            Thread.sleep(10)
        }
    }

    private fun blockedSessions() =
        jdbcTemplate.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null", Int::class.java)!!

    private fun student(studentNumber: Int) =
        studentRepository.save(Student(studentNumber, "학생$studentNumber", LocalDate.of(2009, 7, 28), "s$studentNumber@dsm.hs.kr"))
}
