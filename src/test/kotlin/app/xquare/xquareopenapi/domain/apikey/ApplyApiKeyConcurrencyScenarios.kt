package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationRequest
import app.xquare.xquareopenapi.domain.apikey.usecase.ApplyApiKeyUseCase
import app.xquare.xquareopenapi.domain.auth.FakeEmailVerifiedTokenRepository
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 스파이 없이 실제로 동시에 신청해, 중복 검사·행 잠금·unique 제약 중 어디서 걸리든 키와 분야가 하나씩만 생기는지 확인한다.
// H2(ApplyApiKeyConcurrencyTest)와 운영 DB인 MySQL(ApplyApiKeyMySqlConcurrencyTest)에서 같은 시나리오를 돌린다.
// MySQL은 REPEATABLE READ라 잠금을 기다린 뒤의 일반 조회가 옛 스냅샷을 봐서, H2와 다른 곳(unique 제약)에서 걸린다.
abstract class ApplyApiKeyConcurrencyScenarios {
    @Autowired
    private lateinit var applyApiKeyUseCase: ApplyApiKeyUseCase

    @Autowired
    private lateinit var apiKeyRepository: ApiKeyRepository

    @Autowired
    private lateinit var apiKeyApplicationRepository: ApiKeyApplicationRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var emailVerifiedTokens: FakeEmailVerifiedTokenRepository

    private var userId = 0L

    @BeforeEach
    fun setUp() {
        userId = userRepository.saveUser("junseo").id!!
    }

    @AfterEach
    fun tearDown() {
        apiKeyApplicationRepository.deleteAll()
        apiKeyRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @RepeatedTest(3)
    fun `같은 사용자가 같은 분야로 동시에 첫 신청하면 키와 신청 기록이 하나만 생긴다`() {
        val results = applyConcurrently(ApiKeyField.STUDENT)

        assertEquals(1, results.count { it == null })
        // 키가 저장되기 전에 들어온 요청은 키 제약에, 저장된 뒤에 들어온 요청은 분야 중복 검사에 걸린다.
        assertTrue(
            results.filterNotNull().all { it == ErrorCode.API_KEY_ALREADY_EXISTS || it == ErrorCode.API_KEY_FIELD_ALREADY_APPLIED },
            "$results",
        )
        assertEquals(1, apiKeyRepository.count())
        assertEquals(1, apiKeyApplicationRepository.count())
    }

    @RepeatedTest(3)
    fun `키가 있는 사용자가 같은 분야를 동시에 더하면 하나만 추가되고 나머지는 API_KEY_FIELD_ALREADY_APPLIED`() {
        saveApiKey(apiKeyRepository, apiKeyApplicationRepository, userId, "xq_existing", ApiKeyField.STUDENT)

        val results = applyConcurrently(ApiKeyField.TEACHER)

        assertEquals(1, results.count { it == null })
        assertEquals(THREADS - 1, results.count { it == ErrorCode.API_KEY_FIELD_ALREADY_APPLIED })
        assertEquals(2, apiKeyApplicationRepository.count())
    }

    // 먼저 끝난 요청이 토큰을 지워도 나머지가 인증에 걸리지 않도록 스레드마다 토큰을 따로 둔다.
    // XquareException이 아닌 예외(예: 바꾸지 못한 제약 위반)는 get()에서 그대로 터져 테스트가 실패한다.
    private fun applyConcurrently(field: ApiKeyField): List<ErrorCode?> {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        val results = (1..THREADS).map { index ->
            val token = "token-$index"
            emailVerifiedTokens.tokens[token] = "junseo@dsm.hs.kr"
            executor.submit<ErrorCode?> {
                barrier.await()
                try {
                    applyApiKeyUseCase.execute(userId, ApiKeyApplicationRequest(field, "급식 알리미", "반 단톡방에 급식 공유", token))
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
    }
}
