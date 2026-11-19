package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.apikey.usecase.ReissueApiKeyUseCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals

// 동시에 재발급해도 각 요청은 키를 받고, 마지막으로 저장된 키 하나만 남는다. 받은 키 중 하나는 꼭 쓸 수 있어야 한다.
@SpringBootTest
class ReissueApiKeyConcurrencyTest @Autowired constructor(
    private val reissueApiKeyUseCase: ReissueApiKeyUseCase,
    private val apiKeyRepository: ApiKeyRepository,
) {

    @AfterEach
    fun tearDown() {
        apiKeyRepository.deleteAll()
    }

    @RepeatedTest(3)
    fun `같은 사용자가 동시에 재발급하면 받은 키 중 하나만 남는다`() {
        apiKeyRepository.save(ApiKey(2L, "급식 알리미", "급식 공유", ApiKeySecret.hash("xq_old")))
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)

        val keys = (1..THREADS).map {
            executor.submit<String> {
                barrier.await()
                reissueApiKeyUseCase.execute(2L).apiKey
            }
        }.map { it.get() }
        executor.shutdown()

        val saved = apiKeyRepository.findAll().single()
        assertEquals(1, keys.count { ApiKeySecret.hash(it) == saved.keyHash })
    }

    companion object {
        private const val THREADS = 4
    }
}
