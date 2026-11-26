package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyEmailVerificationRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApplyProjectFieldsRequest
import app.xquare.xquareopenapi.domain.apikey.usecase.ApplyProjectFieldsUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.DeleteProjectUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.ReissueProjectApiKeyUseCase
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

// 프로젝트 행 잠금으로 재발급·삭제·분야 신청이 한 줄로 서는지 실제 동시 요청으로 확인한다.
// H2(ProjectManageConcurrencyTest)와 운영 DB인 MySQL(ProjectManageMySqlConcurrencyTest)에서 같은 시나리오를 돌린다.
abstract class ProjectManageConcurrencyScenarios {
    @Autowired
    private lateinit var deleteProjectUseCase: DeleteProjectUseCase

    @Autowired
    private lateinit var reissueProjectApiKeyUseCase: ReissueProjectApiKeyUseCase

    @Autowired
    private lateinit var applyProjectFieldsUseCase: ApplyProjectFieldsUseCase

    @Autowired
    private lateinit var projectRepository: ApiProjectRepository

    @Autowired
    private lateinit var fieldRepository: ApiProjectFieldRepository

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
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
        userRepository.deleteAll()
        emailVerifiedTokens.tokens.clear()
    }

    @RepeatedTest(3)
    fun `같은 프로젝트를 동시에 지우면 하나만 지우고 나머지는 PROJECT_NOT_FOUND`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, "xq_key", ApiKeyField.CLUB)

        val results = concurrently(List(THREADS) { { _: String -> deleteProjectUseCase.execute(userId, project.id!!) } })

        assertEquals(1, results.count { it == null }, "$results")
        assertEquals(THREADS - 1, results.count { it == ErrorCode.PROJECT_NOT_FOUND }, "$results")
        assertEquals(0, projectRepository.count())
        assertEquals(0, fieldRepository.count())
    }

    @RepeatedTest(3)
    fun `삭제와 분야 신청이 겹쳐도 지운 프로젝트에 분야가 남지 않는다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, "xq_key", ApiKeyField.CLUB)
        val fields = listOf(ApiKeyField.SCHEDULE, ApiKeyField.STUDENT, ApiKeyField.TEACHER)
        val actions = listOf { _: String -> deleteProjectUseCase.execute(userId, project.id!!) } +
            fields.map { field ->
                { token: String ->
                    applyProjectFieldsUseCase.execute(userId, project.id!!, ApplyProjectFieldsRequest(listOf(ApplyProjectFieldsRequest.FieldApplication(field, "목적")), token))
                    Unit
                }
            }

        val results = concurrently(actions)

        assertEquals(null, results[0], "$results")
        assertTrue(results.drop(1).all { it == null || it == ErrorCode.PROJECT_NOT_FOUND }, "$results")
        assertEquals(0, projectRepository.count())
        assertEquals(0, fieldRepository.count())
    }

    @RepeatedTest(3)
    fun `동시에 재발급하면 모두 키를 받지만 마지막에 저장된 키 하나만 남는다`() {
        val project = saveApiKey(projectRepository, fieldRepository, userId, "xq_key", ApiKeyField.CLUB)
        val keys = java.util.concurrent.ConcurrentLinkedQueue<String>()

        val results = concurrently(
            List(THREADS) {
                { token: String ->
                    keys += reissueProjectApiKeyUseCase.execute(userId, project.id!!, ApiKeyEmailVerificationRequest(token)).apiKey
                    Unit
                }
            },
        )

        assertTrue(results.all { it == null }, "$results")
        val saved = projectRepository.findById(project.id!!).orElseThrow().keyHash
        assertEquals(1, keys.count { ApiKeySecret.hash(it) == saved })
    }

    // 먼저 끝난 요청이 토큰을 지워도 나머지가 인증에 걸리지 않도록 요청마다 토큰을 따로 둔다.
    // XquareException이 아닌 예외(예: 교착, stale state)는 get()에서 그대로 터져 테스트가 실패한다.
    private fun concurrently(actions: List<(String) -> Unit>): List<ErrorCode?> {
        val barrier = CyclicBarrier(actions.size)
        val executor = Executors.newFixedThreadPool(actions.size)
        val results = actions.mapIndexed { index, action ->
            val token = "token-$index"
            emailVerifiedTokens.tokens[token] = "junseo@dsm.hs.kr"
            executor.submit<ErrorCode?> {
                barrier.await()
                try {
                    action(token)
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
