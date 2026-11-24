package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApplyProjectFieldsRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.CreateProjectRequest
import app.xquare.xquareopenapi.domain.apikey.usecase.ApplyProjectFieldsUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.CreateProjectUseCase
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
import kotlin.test.assertNotNull

// 스파이 없이 실제로 동시에 요청해, 잠금·중복 검사·unique 제약 중 어디서 걸리든 상한과 중복이 지켜지는지 확인한다.
// H2(ProjectApplyConcurrencyTest)와 운영 DB인 MySQL(ProjectApplyMySqlConcurrencyTest)에서 같은 시나리오를 돌린다.
abstract class ProjectApplyConcurrencyScenarios {
    @Autowired
    private lateinit var createProjectUseCase: CreateProjectUseCase

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
    fun `같은 프로젝트에 같은 분야를 동시에 신청하면 하나만 들어가고 나머지는 FIELD_ALREADY_APPLIED`() {
        val project = projectRepository.saveProject(userId)

        val results = concurrently { token ->
            applyProjectFieldsUseCase.execute(userId, project.id!!, ApplyProjectFieldsRequest(listOf(ApiKeyField.CLUB), token))
        }

        assertEquals(1, results.count { it == null }, "$results")
        assertEquals(THREADS - 1, results.count { it == ErrorCode.FIELD_ALREADY_APPLIED }, "$results")
        assertEquals(1, fieldRepository.count())
        // 첫 승인으로 키가 한 번만 발급됐다.
        assertNotNull(projectRepository.findById(project.id!!).orElseThrow().keyHash)
    }

    @RepeatedTest(3)
    fun `프로젝트가 4개인 사용자가 동시에 만들면 하나만 만들어지고 나머지는 PROJECT_LIMIT_EXCEEDED`() {
        (1..4).forEach { projectRepository.saveProject(userId, name = "프로젝트 $it") }

        val results = concurrently { token -> create("새 프로젝트 $token", token) }

        assertEquals(1, results.count { it == null }, "$results")
        assertEquals(THREADS - 1, results.count { it == ErrorCode.PROJECT_LIMIT_EXCEEDED }, "$results")
        assertEquals(5, projectRepository.countByUserId(userId))
    }

    @RepeatedTest(3)
    fun `같은 이름으로 동시에 만들면 하나만 만들어지고 나머지는 PROJECT_NAME_DUPLICATED`() {
        val results = concurrently { token -> create("급식 알리미", token) }

        assertEquals(1, results.count { it == null }, "$results")
        assertEquals(THREADS - 1, results.count { it == ErrorCode.PROJECT_NAME_DUPLICATED }, "$results")
        assertEquals(1, projectRepository.count())
        assertEquals(1, fieldRepository.count())
    }

    private fun create(name: String, token: String) {
        createProjectUseCase.execute(userId, CreateProjectRequest(name, "급식 공유", listOf(ApiKeyField.CLUB), token))
    }

    // 먼저 끝난 요청이 토큰을 지워도 나머지가 인증에 걸리지 않도록 스레드마다 토큰을 따로 둔다.
    // XquareException이 아닌 예외(예: 바꾸지 못한 제약 위반, 교착)는 get()에서 그대로 터져 테스트가 실패한다.
    private fun concurrently(action: (String) -> Unit): List<ErrorCode?> {
        val barrier = CyclicBarrier(THREADS)
        val executor = Executors.newFixedThreadPool(THREADS)
        val results = (1..THREADS).map { index ->
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
