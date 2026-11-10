package app.xquare.xquareopenapi.domain.meal

import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealRequest
import app.xquare.xquareopenapi.domain.meal.usecase.CreateMealUseCase
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mockingDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 같은 '급식 없음' 표시에 등록 두 개가 겹치는 상황을 실제 스레드로 만든다(H2, #24 리뷰).
// 잠금 없이 읽던 때는 둘 다 표시를 보고 고쳐 둘 다 201이 되고, 먼저 등록한 메뉴가 에러 없이 사라졌다.
@SpringBootTest
class CreateMealConcurrencyTest @Autowired constructor(
    private val mealRepository: MealRepository,
    private val createMealUseCase: CreateMealUseCase,
    private val jdbcTemplate: JdbcTemplate,
) {
    // 먼저 온 등록이 표시 행을 잠근 직후 멈춰 세우는 데 쓴다.
    // 저장소는 JDK 프록시라 진짜 스파이가 아니라 실제 빈에 위임하는 목이다. 실제 동작은 callRealMethod 대신 기본 응답(위임)으로 부른다.
    @MockitoSpyBean
    private lateinit var spiedMealRepository: MealRepository

    @AfterEach
    fun tearDown() {
        mealRepository.deleteAll()
    }

    @RepeatedTest(3)
    fun `같은 급식 없음 날짜에 동시에 등록하면 먼저 잠근 쪽만 등록되고 늦은 쪽은 MEAL_ALREADY_EXISTS`() {
        val date = LocalDate.of(2026, 9, 1)
        val marker = mealRepository.save(Meal.noMeal(date))
        val executor = Executors.newFixedThreadPool(2)
        val locked = CountDownLatch(1)
        val first = AtomicBoolean(true)
        doAnswer { invocation ->
            val found = mockingDetails(spiedMealRepository).mockCreationSettings.defaultAnswer.answer(invocation)
            // 처음 한 번만: 표시 행을 잠근 채로, 늦은 등록이 이 잠금을 기다리기 시작할 때까지 멈춘다.
            if (first.getAndSet(false)) {
                locked.countDown()
                awaitBlockedSession()
            }
            found
        }.`when`(spiedMealRepository).findByIdForUpdate(anyLong())

        val earlier = executor.submit<Long> { createMealUseCase.execute(request(date, "먼저밥")) }
        assertTrue(locked.await(5, TimeUnit.SECONDS))
        val later = executor.submit<Long> { createMealUseCase.execute(request(date, "나중밥")) }
        val earlierResult = runCatching { earlier.get(10, TimeUnit.SECONDS) }
        val laterResult = runCatching { later.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(marker.id, earlierResult.getOrThrow())
        assertEquals(ErrorCode.MEAL_ALREADY_EXISTS, (laterResult.exceptionOrNull()?.cause as? XquareException)?.errorCode)
        val saved = mealRepository.findAll().single()
        assertEquals(listOf("먼저밥"), saved.lunch)
    }

    private fun request(date: LocalDate, lunch: String) = MealRequest(date, emptyList(), listOf(lunch), emptyList())

    // H2가 잠금을 기다리는 세션을 보여 줄 때까지 기다린다. 잠금이 없으면 늦은 등록이 기다리지 않아 여기서 실패한다.
    private fun awaitBlockedSession() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (blockedSessions() == 0) {
            check(System.nanoTime() < deadline) { "잠금을 기다리는 세션이 없습니다." }
            Thread.sleep(10)
        }
    }

    private fun blockedSessions() =
        jdbcTemplate.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null", Int::class.java)!!
}
