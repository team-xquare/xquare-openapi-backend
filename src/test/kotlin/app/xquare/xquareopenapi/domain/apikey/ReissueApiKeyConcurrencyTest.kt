package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ReissueApiKeyRequest
import app.xquare.xquareopenapi.domain.apikey.usecase.ReissueApiKeyUseCase
import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import app.xquare.xquareopenapi.domain.auth.FakeEmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.user.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals

// 동시에 재발급해도 각 요청은 키를 받고, 마지막으로 저장된 키 하나만 남는다. 받은 키 중 하나는 꼭 쓸 수 있어야 한다.
@SpringBootTest
@Import(FakeAuthRepositories::class)
class ReissueApiKeyConcurrencyTest @Autowired constructor(
    private val reissueApiKeyUseCase: ReissueApiKeyUseCase,
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
    private val userRepository: UserRepository,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
) {

    @AfterEach
    fun tearDown() {
        apiKeyApplicationRepository.deleteAll()
        apiKeyRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @RepeatedTest(3)
    fun `같은 사용자가 동시에 재발급하면 받은 키 중 하나만 남는다`() {
        val userId = userRepository.saveUser("junseo").id!!
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_old", ApiKeyField.STUDENT)
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)

        // 먼저 끝난 요청이 토큰을 지워도 나머지가 인증에 걸리지 않도록 스레드마다 토큰을 따로 둔다.
        val keys = (1..THREADS).map { index ->
            emailVerifiedTokens.tokens["token-$index"] = "junseo@dsm.hs.kr"
            executor.submit<String> {
                barrier.await()
                reissueApiKeyUseCase.execute(userId, ReissueApiKeyRequest("token-$index")).apiKey
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
