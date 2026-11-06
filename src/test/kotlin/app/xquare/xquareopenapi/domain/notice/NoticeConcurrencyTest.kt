package app.xquare.xquareopenapi.domain.notice

import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeRequest
import app.xquare.xquareopenapi.domain.notice.usecase.DeleteNoticeUseCase
import app.xquare.xquareopenapi.domain.notice.usecase.UpdateNoticeUseCase
import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 조회 후 고치거나 지우면 그 사이에 다른 요청이 지웠을 때 0행 수정·삭제로 500이 난다. 한 번에 고치고 지워서 404로 끝나는지 본다.
@SpringBootTest
class NoticeConcurrencyTest @Autowired constructor(
    private val updateNoticeUseCase: UpdateNoticeUseCase,
    private val deleteNoticeUseCase: DeleteNoticeUseCase,
    private val noticeRepository: NoticeRepository,
    private val userRepository: UserRepository,
) {

    @AfterEach
    fun tearDown() {
        noticeRepository.deleteAll()
        userRepository.deleteAll(userRepository.findAll().filter { it.username == USERNAME })
    }

    @RepeatedTest(3)
    fun `같은 공지를 동시에 지우면 하나만 성공하고 나머지는 NOTICE_NOT_FOUND`() {
        val id = saveNotice()

        val results = runConcurrently(THREADS) { deleteNoticeUseCase.execute(id) }

        assertEquals(1, results.count { it == null })
        assertEquals(THREADS - 1, results.count { it == ErrorCode.NOTICE_NOT_FOUND })
        assertFalse(noticeRepository.existsById(id))
    }

    @RepeatedTest(3)
    fun `수정과 삭제가 겹쳐도 500 없이 수정은 성공하거나 NOTICE_NOT_FOUND`() {
        val id = saveNotice()

        val results = runConcurrently(THREADS) { index ->
            if (index == 0) deleteNoticeUseCase.execute(id) else updateNoticeUseCase.execute(id, NoticeRequest("바뀐 제목", "바뀐 본문"))
        }

        assertTrue(results.all { it == null || it == ErrorCode.NOTICE_NOT_FOUND })
        assertEquals(null, results[0])
        assertFalse(noticeRepository.existsById(id))
    }

    private fun saveNotice(): Long {
        val author = userRepository.findAll().firstOrNull { it.username == USERNAME }
            ?: userRepository.save(User(USERNAME, "encoded", 2315, "김관리", "notice-concurrency@dsm.hs.kr"))
        return noticeRepository.save(Notice("제목", "본문", author, LocalDateTime.of(2026, 9, 1, 9, 0))).id!!
    }

    // 모든 스레드가 모인 뒤 한꺼번에 실행하고, 스레드마다 성공이면 null, XquareException이면 그 에러 코드를 돌려준다.
    private fun runConcurrently(threads: Int, action: (Int) -> Unit): List<ErrorCode?> {
        val barrier = CyclicBarrier(threads)
        val executor = Executors.newFixedThreadPool(threads)
        val results = (0 until threads).map { index ->
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

    companion object {
        private const val THREADS = 4
        private const val USERNAME = "noticeconcur"
    }
}
