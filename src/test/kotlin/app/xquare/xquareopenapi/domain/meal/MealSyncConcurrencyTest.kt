package app.xquare.xquareopenapi.domain.meal

import app.xquare.xquareopenapi.domain.meal.neis.NeisMeal
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealRequest
import app.xquare.xquareopenapi.domain.meal.usecase.MealSyncResult
import app.xquare.xquareopenapi.domain.meal.usecase.SyncNeisMealsUseCase
import app.xquare.xquareopenapi.domain.meal.usecase.UpdateMealUseCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mockingDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 나이스 동기화가 급식 행을 잠근 채 메뉴를 바꾸는 동안 관리자 수정이 들어와도, 관리자 수정이 기다렸다가 덮어써서 남는지 본다(H2).
@SpringBootTest
class MealSyncConcurrencyTest @Autowired constructor(
    private val mealRepository: MealRepository,
    private val syncNeisMealsUseCase: SyncNeisMealsUseCase,
    private val updateMealUseCase: UpdateMealUseCase,
) {
    // 동기화가 행을 잠근 직후 멈춰 세우는 데 쓴다.
    // 저장소는 JDK 프록시라 진짜 스파이가 아니라 실제 빈에 위임하는 목이다. 실제 동작은 callRealMethod 대신 기본 응답(위임)으로 부른다.
    @MockitoSpyBean
    private lateinit var spiedMealRepository: MealRepository

    @AfterEach
    fun tearDown() {
        mealRepository.deleteAll()
    }

    @RepeatedTest(3)
    fun `동기화가 행을 잠근 동안 들어온 관리자 수정은 동기화가 끝난 뒤 반영되어 남는다`() {
        val date = LocalDate.of(2026, 10, 1)
        val meal = mealRepository.save(Meal(date, listOf("죽"), listOf("백미밥"), listOf("카레"), editedByAdmin = false))
        val executor = Executors.newFixedThreadPool(2)
        val locked = CountDownLatch(1)
        val update = AtomicReference<Future<*>>()
        val updateDoneWhileLocked = AtomicBoolean(true)
        doAnswer { invocation ->
            val found = mockingDetails(spiedMealRepository).mockCreationSettings.defaultAnswer.answer(invocation)
            locked.countDown()
            // 관리자 수정이 이 잠금에 막혀 기다리는지 본다. H2의 기본 잠금 대기 시간(1초 이상)보다 짧게 잡는다.
            Thread.sleep(HOLD_MILLIS)
            updateDoneWhileLocked.set(update.get().isDone)
            found
        }.`when`(spiedMealRepository).findAllByDateIn(anyCollection())

        val sync = executor.submit<MealSyncResult> {
            syncNeisMealsUseCase.execute(listOf(NeisMeal(date, emptyList(), listOf("나이스밥"), emptyList())))
        }
        assertTrue(locked.await(5, TimeUnit.SECONDS))
        update.set(executor.submit {
            updateMealUseCase.execute(meal.id!!, MealRequest(date, emptyList(), listOf("관리자밥"), emptyList()))
        })
        val syncResult = sync.get(10, TimeUnit.SECONDS)
        update.get().get(10, TimeUnit.SECONDS)
        executor.shutdown()

        // 동기화는 잠근 시점에 관리자 수정 전 값을 보고 메뉴를 바꿨고, 관리자 수정은 그 커밋을 기다렸다가 덮어썼다.
        assertFalse(updateDoneWhileLocked.get())
        assertEquals(MealSyncResult(created = 0, updated = 1, skipped = 0), syncResult)
        val saved = mealRepository.findById(meal.id!!).get()
        assertEquals(emptyList(), saved.breakfast)
        assertEquals(listOf("관리자밥"), saved.lunch)
        assertEquals(emptyList(), saved.dinner)
        assertTrue(saved.editedByAdmin)

        // 이후 동기화는 관리자 수정을 건너뛴다.
        assertEquals(
            MealSyncResult(created = 0, updated = 0, skipped = 1),
            syncNeisMealsUseCase.execute(listOf(NeisMeal(date, emptyList(), listOf("나이스밥"), emptyList()))),
        )
        assertEquals(listOf("관리자밥"), mealRepository.findById(meal.id!!).get().lunch)
    }

    companion object {
        private const val HOLD_MILLIS = 500L
    }
}
