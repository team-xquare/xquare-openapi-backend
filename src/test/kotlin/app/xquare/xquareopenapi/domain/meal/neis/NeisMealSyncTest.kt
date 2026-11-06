package app.xquare.xquareopenapi.domain.meal.neis

import app.xquare.xquareopenapi.domain.meal.Meal
import app.xquare.xquareopenapi.domain.meal.MealRepository
import app.xquare.xquareopenapi.domain.meal.neis.NeisMealClientTest.Companion.NO_DATA
import app.xquare.xquareopenapi.domain.meal.neis.NeisMealClientTest.Companion.row
import app.xquare.xquareopenapi.domain.meal.neis.NeisMealClientTest.Companion.rows
import app.xquare.xquareopenapi.domain.meal.usecase.MealSyncResult
import app.xquare.xquareopenapi.domain.meal.usecase.SyncNeisMealsUseCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class NeisMealSyncTest @Autowired constructor(
    private val mealRepository: MealRepository,
    private val syncNeisMealsUseCase: SyncNeisMealsUseCase,
) {
    private val builder = RestClient.builder().baseUrl("https://open.neis.go.kr")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val restClient = builder.build()

    // 서울 시간으로 2026-12-31 23:30. UTC로는 아직 12월 31일 14:30이다.
    private val clock = Clock.fixed(Instant.parse("2026-12-31T14:30:00Z"), ZoneId.of("Asia/Seoul"))

    @AfterEach
    fun tearDown() {
        mealRepository.deleteAll()
    }

    // 저장

    @Test
    fun `없는 날짜는 나이스 급식으로 새로 만든다`() {
        val result = syncNeisMealsUseCase.execute(
            listOf(NeisMeal(LocalDate.of(2026, 10, 1), listOf("죽"), listOf("백미밥", "김치"), listOf("카레"))),
        )

        assertEquals(MealSyncResult(created = 1, updated = 0, skipped = 0), result)
        val meal = mealRepository.findAll().single()
        assertEquals(LocalDate.of(2026, 10, 1), meal.date)
        assertEquals(listOf("죽"), meal.breakfast)
        assertEquals(listOf("백미밥", "김치"), meal.lunch)
        assertEquals(listOf("카레"), meal.dinner)
        assertFalse(meal.editedByAdmin)
    }

    @Test
    fun `나이스에서 온 급식은 새 메뉴로 바꾼다`() {
        val meal = mealRepository.save(Meal(LocalDate.of(2026, 10, 1), listOf("죽"), listOf("백미밥"), listOf("카레"), editedByAdmin = false))

        val result = syncNeisMealsUseCase.execute(
            listOf(NeisMeal(LocalDate.of(2026, 10, 1), emptyList(), listOf("잡곡밥"), listOf("짜장면"))),
        )

        assertEquals(MealSyncResult(created = 0, updated = 1, skipped = 0), result)
        assertEquals(1, result.updated)
        val updated = mealRepository.findById(meal.id!!).get()
        assertEquals(emptyList(), updated.breakfast)
        assertEquals(listOf("잡곡밥"), updated.lunch)
        assertEquals(listOf("짜장면"), updated.dinner)
        assertFalse(updated.editedByAdmin)
        assertEquals(1, mealRepository.count())
    }

    @Test
    fun `관리자가 등록하거나 수정한 급식은 덮어쓰지 않는다`() {
        val meal = mealRepository.save(Meal(LocalDate.of(2026, 10, 1), listOf("죽"), listOf("백미밥"), listOf("카레"), editedByAdmin = true))

        val result = syncNeisMealsUseCase.execute(
            listOf(
                NeisMeal(LocalDate.of(2026, 10, 1), emptyList(), listOf("잡곡밥"), emptyList()),
                NeisMeal(LocalDate.of(2026, 10, 2), emptyList(), listOf("떡국"), emptyList()),
            ),
        )

        assertEquals(MealSyncResult(created = 1, updated = 0, skipped = 1), result)
        assertEquals(listOf(1, 0, 1), listOf(result.created, result.updated, result.skipped))
        val kept = mealRepository.findById(meal.id!!).get()
        assertEquals(listOf("죽"), kept.breakfast)
        assertEquals(listOf("백미밥"), kept.lunch)
        assertEquals(listOf("카레"), kept.dinner)
        assertTrue(kept.editedByAdmin)
        assertEquals(listOf("떡국"), mealRepository.findAll().single { it.date == LocalDate.of(2026, 10, 2) }.lunch)
    }

    @Test
    fun `가져온 급식이 없으면 아무것도 바꾸지 않는다`() {
        mealRepository.save(Meal(LocalDate.of(2026, 10, 1), listOf("죽"), listOf("백미밥"), listOf("카레"), editedByAdmin = false))

        assertEquals(MealSyncResult(created = 0, updated = 0, skipped = 0), syncNeisMealsUseCase.execute(emptyList()))
        assertEquals(listOf("백미밥"), mealRepository.findAll().single().lunch)
    }

    // 스케줄러

    @Test
    fun `서울 시간 기준 이번 달과 다음 달을 가져와 저장한다`() {
        expectMonth("20261201", "20261231", rows(row("20261231", "2", "떡국 (5)")))
        expectMonth("20270101", "20270131", rows(row("20270104", "3", "카레 (5)<br/>김치 (9)")))

        scheduler().sync()

        server.verify()
        val meals = mealRepository.findAll().sortedBy(Meal::date)
        assertEquals(listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 4)), meals.map(Meal::date))
        assertEquals(listOf("떡국"), meals[0].lunch)
        assertEquals(listOf("카레", "김치"), meals[1].dinner)
    }

    @Test
    fun `한 달이 실패해도 다음 달은 저장한다`() {
        server.expect(queryParam("MLSV_FROM_YMD", "20261201")).andRespond(withServerError())
        expectMonth("20270101", "20270131", rows(row("20270104", "2", "백미밥")))

        scheduler().sync()

        server.verify()
        assertEquals(listOf(LocalDate.of(2027, 1, 4)), mealRepository.findAll().map(Meal::date))
    }

    @Test
    fun `데이터가 없는 달은 건너뛴다`() {
        expectMonth("20261201", "20261231", NO_DATA)
        expectMonth("20270101", "20270131", NO_DATA)

        scheduler().sync()

        server.verify()
        assertEquals(0, mealRepository.count())
    }

    @Test
    fun `API 키가 없으면 나이스를 부르지 않는다`() {
        listOf("", " ").forEach { apiKey ->
            // 기대한 요청이 없으므로 요청을 보내면 AssertionError가 나서 테스트가 실패한다.
            scheduler(apiKey).sync()
        }

        server.verify()
        assertEquals(0, mealRepository.count())
    }

    private fun scheduler(apiKey: String = "test-key"): NeisMealSyncScheduler {
        val properties = NeisProperties(apiKey, "G10", "7430310")
        return NeisMealSyncScheduler(NeisMealClient(restClient, properties), syncNeisMealsUseCase, properties, clock)
    }

    private fun expectMonth(from: String, to: String, body: String) {
        server.expect(queryParam("MLSV_FROM_YMD", from))
            .andExpect(queryParam("MLSV_TO_YMD", to))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }
}
