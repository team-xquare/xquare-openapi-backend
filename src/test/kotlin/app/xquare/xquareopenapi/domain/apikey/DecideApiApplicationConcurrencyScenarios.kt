package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.apikey.usecase.CancelProjectFieldUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.DecideApiApplicationUseCase
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// 여러 관리자가 같은 신청을 동시에 처리해도 한 번만 처리되는지 실제 동시 요청으로 확인한다.
// H2(DecideApiApplicationConcurrencyTest)와 운영 DB인 MySQL(DecideApiApplicationMySqlConcurrencyTest)에서 같은 시나리오를 돌린다.
abstract class DecideApiApplicationConcurrencyScenarios {
    @Autowired
    private lateinit var decideApiApplicationUseCase: DecideApiApplicationUseCase

    @Autowired
    private lateinit var cancelProjectFieldUseCase: CancelProjectFieldUseCase

    @Autowired
    private lateinit var projectRepository: ApiProjectRepository

    @Autowired
    private lateinit var fieldRepository: ApiProjectFieldRepository

    @AfterEach
    fun tearDown() {
        fieldRepository.deleteAll()
        projectRepository.deleteAll()
    }

    @RepeatedTest(3)
    fun `같은 신청을 동시에 승인, 반려하면 하나만 처리되고 나머지는 APPLICATION_ALREADY_DECIDED`() {
        val project = projectRepository.saveProject(2L)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        val results = concurrently(
            List(THREADS) { index ->
                {
                    if (index % 2 == 0) {
                        decideApiApplicationUseCase.approve(index + 1L, application.id!!)
                    } else {
                        decideApiApplicationUseCase.reject(index + 1L, application.id!!, "사유 $index")
                    }
                    Unit
                }
            },
        )

        assertEquals(1, results.count { it == null }, "$results")
        assertEquals(THREADS - 1, results.count { it == ErrorCode.APPLICATION_ALREADY_DECIDED }, "$results")
        assertTrue(fieldRepository.findById(application.id!!).orElseThrow().status != ApplicationStatus.PENDING)
    }

    @RepeatedTest(3)
    fun `키가 없는 프로젝트의 여러 신청을 동시에 승인해도 모두 승인되고 키가 발급된다`() {
        val project = projectRepository.saveProject(2L)
        val applications = listOf(ApiKeyField.STUDENT, ApiKeyField.TEACHER).map {
            fieldRepository.saveField(project.id!!, it, ApplicationStatus.PENDING)
        }

        val results = concurrently(applications.map { application -> { decideApiApplicationUseCase.approve(1L, application.id!!); Unit } })

        assertTrue(results.all { it == null }, "$results")
        assertTrue(fieldRepository.findAll().all { it.status == ApplicationStatus.APPROVED })
        assertNotNull(projectRepository.findById(project.id!!).orElseThrow().keyHash)
    }

    @RepeatedTest(3)
    fun `승인과 사용자의 신청 취소가 겹치면 둘 중 하나만 처리된다`() {
        val project = projectRepository.saveProject(2L)
        val application = fieldRepository.saveField(project.id!!, ApiKeyField.STUDENT, ApplicationStatus.PENDING)

        val (approved, cancelled) = concurrently(
            listOf(
                { decideApiApplicationUseCase.approve(1L, application.id!!); Unit },
                { cancelProjectFieldUseCase.execute(2L, project.id!!, ApiKeyField.STUDENT) },
            ),
        )

        val remaining = fieldRepository.findAll()
        if (approved == null) {
            // 승인이 먼저 끝났으면 취소는 이미 처리된 신청이라 거절되고 승인된 신청이 남는다.
            assertEquals(ErrorCode.APPLICATION_NOT_PENDING, cancelled)
            assertEquals(ApplicationStatus.APPROVED, remaining.single().status)
        } else {
            // 취소가 먼저 끝났으면 승인할 신청이 없다.
            assertEquals(ErrorCode.APPLICATION_NOT_FOUND, approved)
            assertEquals(null, cancelled)
            assertTrue(remaining.isEmpty())
        }
    }

    // XquareException이 아닌 예외(예: 교착, stale state)는 get()에서 그대로 터져 테스트가 실패한다.
    private fun concurrently(actions: List<() -> Unit>): List<ErrorCode?> {
        val barrier = CyclicBarrier(actions.size)
        val executor = Executors.newFixedThreadPool(actions.size)
        val results = actions.map { action ->
            executor.submit<ErrorCode?> {
                barrier.await()
                try {
                    action()
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
