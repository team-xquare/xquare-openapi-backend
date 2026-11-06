package app.xquare.xquareopenapi.domain.club

import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubRequest
import app.xquare.xquareopenapi.domain.club.usecase.UpdateClubUseCase
import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.student.StudentRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 학생 행 잠금을 실제 스레드 여러 개로 겹쳐 본다. ClubTest는 스파이로 중복 검사만 통과시켜 unique 제약을 확인한다.
@SpringBootTest
class ClubConcurrencyTest @Autowired constructor(
    private val updateClubUseCase: UpdateClubUseCase,
    private val clubRepository: ClubRepository,
    private val studentRepository: StudentRepository,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
) {

    @AfterEach
    fun tearDown() {
        clubRepository.deleteAll()
        studentRepository.deleteAll()
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
