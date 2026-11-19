package app.xquare.xquareopenapi.domain.meal

import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealRequest
import app.xquare.xquareopenapi.domain.meal.usecase.CreateMealUseCase
import app.xquare.xquareopenapi.domain.meal.usecase.UpdateMealUseCase
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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// '급식 없음' 날짜로 급식을 옮기는 수정이 같은 날짜의 등록·다른 수정과 겹치는 상황을 실제 스레드로 만든다(H2, #24 리뷰).
// 표시를 잠그지 않고 읽던 때는, 수정이 표시를 읽은 뒤 등록이 그 표시를 급식으로 바꾸면 수정이 그 급식을 에러 없이 지웠다.
@SpringBootTest
class UpdateMealConcurrencyTest @Autowired constructor(
    private val mealRepository: MealRepository,
    private val createMealUseCase: CreateMealUseCase,
    private val updateMealUseCase: UpdateMealUseCase,
    private val jdbcTemplate: JdbcTemplate,
) {
    // 먼저 온 요청이 표시 행을 잠근 직후 멈춰 세우는 데 쓴다(CreateMealConcurrencyTest와 같은 방식).
    @MockitoSpyBean
    private lateinit var spiedMealRepository: MealRepository

    @AfterEach
    fun tearDown() {
        mealRepository.deleteAll()
    }

    @RepeatedTest(3)
    fun `등록이 먼저 급식 없음 표시를 잠그면 그 날짜로 옮기는 수정은 기다렸다가 MEAL_ALREADY_EXISTS이고 등록한 급식이 남는다`() {
        val marker = mealRepository.save(Meal.noMeal(MARKER_DATE))
        val meal = mealRepository.save(meal(ORIGINAL_DATE, "옮길밥"))

        val (created, moved) = overlap(
            earlier = { createMealUseCase.execute(request(MARKER_DATE, "등록밥")) },
            later = { updateMealUseCase.execute(meal.id!!, request(MARKER_DATE, "옮길밥")) },
        )

        assertEquals(marker.id, created.getOrThrow())
        assertEquals(ErrorCode.MEAL_ALREADY_EXISTS, moved.errorCode())
        assertEquals(
            mapOf(ORIGINAL_DATE to listOf("옮길밥"), MARKER_DATE to listOf("등록밥")),
            mealRepository.findAll().associate { it.date to it.lunch },
        )
    }

    @RepeatedTest(3)
    fun `수정이 먼저 급식 없음 표시를 잠그면 그 날짜의 등록은 기다렸다가 MEAL_ALREADY_EXISTS이고 옮긴 급식이 남는다`() {
        mealRepository.save(Meal.noMeal(MARKER_DATE))
        val meal = mealRepository.save(meal(ORIGINAL_DATE, "옮길밥"))

        val (moved, created) = overlap(
            earlier = { updateMealUseCase.execute(meal.id!!, request(MARKER_DATE, "옮길밥")) },
            later = { createMealUseCase.execute(request(MARKER_DATE, "등록밥")) },
        )

        moved.getOrThrow()
        assertEquals(ErrorCode.MEAL_ALREADY_EXISTS, created.errorCode())
        // 원래 날짜에는 '급식 없음' 표시가 남는다.
        assertEquals(
            mapOf(ORIGINAL_DATE to emptyList(), MARKER_DATE to listOf("옮길밥")),
            mealRepository.findAll().associate { it.date to it.lunch },
        )
    }

    @RepeatedTest(3)
    fun `같은 급식 없음 날짜로 옮기는 수정이 겹치면 먼저 잠근 쪽만 옮기고 늦은 쪽은 MEAL_ALREADY_EXISTS`() {
        mealRepository.save(Meal.noMeal(MARKER_DATE))
        val first = mealRepository.save(meal(ORIGINAL_DATE, "먼저밥"))
        val second = mealRepository.save(meal(OTHER_DATE, "나중밥"))

        val (earlier, later) = overlap(
            earlier = { updateMealUseCase.execute(first.id!!, request(MARKER_DATE, "먼저밥")) },
            later = { updateMealUseCase.execute(second.id!!, request(MARKER_DATE, "나중밥")) },
        )

        earlier.getOrThrow()
        assertEquals(ErrorCode.MEAL_ALREADY_EXISTS, later.errorCode())
        assertEquals(
            mapOf(ORIGINAL_DATE to emptyList(), MARKER_DATE to listOf("먼저밥"), OTHER_DATE to listOf("나중밥")),
            mealRepository.findAll().associate { it.date to it.lunch },
        )
    }

    // earlier가 표시 행을 잠근 채로, later가 그 잠금을 기다리기 시작할 때까지 멈췄다가 이어서 커밋한다.
    private fun <A, B> overlap(earlier: () -> A, later: () -> B): Pair<Result<A>, Result<B>> {
        val executor = Executors.newFixedThreadPool(2)
        val locked = CountDownLatch(1)
        val first = AtomicBoolean(true)
        doAnswer { invocation ->
            val found = mockingDetails(spiedMealRepository).mockCreationSettings.defaultAnswer.answer(invocation)
            if (first.getAndSet(false)) {
                locked.countDown()
                jdbcTemplate.awaitBlockedSession()
            }
            found
        }.`when`(spiedMealRepository).findByIdForUpdate(anyLong())

        val earlierFuture = executor.submit(Callable { earlier() })
        assertTrue(locked.await(5, TimeUnit.SECONDS))
        val laterFuture = executor.submit(Callable { later() })
        val earlierResult = runCatching { earlierFuture.get(10, TimeUnit.SECONDS) }
        val laterResult = runCatching { laterFuture.get(10, TimeUnit.SECONDS) }
        executor.shutdown()
        return earlierResult to laterResult
    }

    private fun Result<*>.errorCode() = (exceptionOrNull()?.cause as? XquareException)?.errorCode

    private fun meal(date: LocalDate, lunch: String) = Meal(date, emptyList(), listOf(lunch), emptyList(), editedByAdmin = true)

    private fun request(date: LocalDate, lunch: String) = MealRequest(date, emptyList(), listOf(lunch), emptyList())

    companion object {
        private val ORIGINAL_DATE = LocalDate.of(2026, 9, 1)
        private val MARKER_DATE = LocalDate.of(2026, 9, 2)
        private val OTHER_DATE = LocalDate.of(2026, 9, 3)
    }
}
