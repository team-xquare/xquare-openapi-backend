package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyRequest
import app.xquare.xquareopenapi.domain.apikey.usecase.IssueApiKeyUseCase
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals

// 스파이 없이 실제로 동시에 신청해, 중복 검사와 unique 제약 중 어디서 걸리든 키가 하나만 생기는지 확인한다.
@SpringBootTest
class IssueApiKeyConcurrencyTest @Autowired constructor(
    private val issueApiKeyUseCase: IssueApiKeyUseCase,
    private val apiKeyRepository: ApiKeyRepository,
) {

    @AfterEach
    fun tearDown() {
        apiKeyRepository.deleteAll()
    }

    @RepeatedTest(3)
    fun `같은 사용자가 동시에 신청하면 하나만 발급되고 나머지는 API_KEY_ALREADY_EXISTS`() {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)

        val results = (1..THREADS).map {
            executor.submit<ErrorCode?> {
                barrier.await()
                try {
                    issueApiKeyUseCase.execute(2L, ApiKeyRequest("급식 알리미", "반 단톡방에 급식 공유"))
                    null
                } catch (e: XquareException) {
                    e.errorCode
                }
            }
        }.map { it.get() }
        executor.shutdown()

        assertEquals(1, results.count { it == null })
        assertEquals(THREADS - 1, results.count { it == ErrorCode.API_KEY_ALREADY_EXISTS })
        assertEquals(1, apiKeyRepository.count())
    }

    companion object {
        private const val THREADS = 4
    }
}
