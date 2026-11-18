package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationRequest
import app.xquare.xquareopenapi.domain.apikey.usecase.ApplyApiKeyUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.DeleteApiKeyUseCase
import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import app.xquare.xquareopenapi.domain.auth.FakeEmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Import(FakeAuthRepositories::class)
class DeleteApiKeyConcurrencyTest @Autowired constructor(
    private val deleteApiKeyUseCase: DeleteApiKeyUseCase,
    private val applyApiKeyUseCase: ApplyApiKeyUseCase,
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
    private val userRepository: UserRepository,
    private val emailVerifiedTokens: FakeEmailVerifiedTokenRepository,
) {
    private var userId = 0L

    @BeforeEach
    fun setUp() {
        userId = userRepository.saveUser("junseo").id!!
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_mine", ApiKeyField.STUDENT)
    }

    @AfterEach
    fun tearDown() {
        apiKeyApplicationRepository.deleteAll()
        apiKeyRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @RepeatedTest(3)
    fun `같은 키를 동시에 지우면 하나만 성공하고 나머지는 API_KEY_NOT_FOUND`() {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        val results = (1..THREADS).map {
            executor.submit<ErrorCode?> {
                barrier.await()
                try {
                    deleteApiKeyUseCase.execute(userId)
                    null
                } catch (e: XquareException) {
                    e.errorCode
                }
            }
        }.map { it.get() }
        executor.shutdown()

        assertEquals(1, results.count { it == null })
        assertEquals(THREADS - 1, results.count { it == ErrorCode.API_KEY_NOT_FOUND })
        assertEquals(0, apiKeyRepository.count())
        assertEquals(0, apiKeyApplicationRepository.count())
    }

    @RepeatedTest(3)
    fun `삭제와 분야 추가가 겹쳐도 지운 키에 신청 기록이 남지 않는다`() {
        emailVerifiedTokens.tokens["token"] = "junseo@dsm.hs.kr"
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        val tasks = listOf(
            executor.submit {
                barrier.await()
                deleteApiKeyUseCase.execute(userId)
            },
            executor.submit {
                barrier.await()
                applyApiKeyUseCase.execute(userId, ApiKeyApplicationRequest(ApiKeyField.TEACHER, "선생님 찾기", "교무실 안내", "token"))
            },
        )
        tasks.forEach { it.get() }
        executor.shutdown()

        // 분야 추가가 먼저면 키와 함께 지워지고, 삭제가 먼저면 첫 신청으로 새 키가 생긴다. 어느 쪽이든 고아 기록은 없다.
        val keyIds = apiKeyRepository.findAll().map { it.id }.toSet()
        assertTrue(apiKeyApplicationRepository.findAll().all { it.apiKeyId in keyIds })
        assertTrue(apiKeyRepository.count() <= 1)
    }

    companion object {
        private const val THREADS = 4
    }
}
