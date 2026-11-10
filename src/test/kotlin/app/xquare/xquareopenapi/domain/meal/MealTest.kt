package app.xquare.xquareopenapi.domain.meal

import app.xquare.xquareopenapi.domain.meal.neis.NeisMeal
import app.xquare.xquareopenapi.domain.meal.usecase.MealSyncResult
import app.xquare.xquareopenapi.domain.meal.usecase.SyncNeisMealsUseCase
import app.xquare.xquareopenapi.domain.user.Role
import app.xquare.xquareopenapi.global.jwt.JwtProvider
import jakarta.servlet.ServletException
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.sql.SQLException
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class MealTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val mealRepository: MealRepository,
    private val syncNeisMealsUseCase: SyncNeisMealsUseCase,
    jwtProvider: JwtProvider,
) {
    // 동시 등록·수정을 흉내 내려고 중복 검사만 통과시킬 때 쓴다.
    @MockitoSpyBean
    private lateinit var spiedMealRepository: MealRepository

    private val adminToken = jwtProvider.generateAccessToken(1L, Role.ADMIN)
    private val userToken = jwtProvider.generateAccessToken(2L, Role.USER)

    @AfterEach
    fun tearDown() {
        mealRepository.deleteAll()
    }

    // 등록

    @Test
    fun `등록하면 저장하고 Location 헤더로 상세조회 URI를 준다`() {
        mockMvc.post("/meals") {
            bearer(adminToken)
            json(body())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.message") { value("생성되었습니다.") }
        }.andReturn().response.also { response ->
            val meal = mealRepository.findAll().single()
            assertEquals("/meals/${meal.id}", response.getHeader("Location"))
            assertEquals(LocalDate.of(2026, 9, 1), meal.date)
            assertEquals(listOf("볶음밥", "미역국", "계란말이"), meal.breakfast)
            assertEquals(listOf("백미밥", "된장찌개", "잡채"), meal.lunch)
            assertEquals(listOf("카레라이스", "김치전"), meal.dinner)
            assertTrue(meal.editedByAdmin)
        }
    }

    @Test
    fun `급식이 없는 끼니는 빈 배열로 등록한다`() {
        mockMvc.post("/meals") {
            bearer(adminToken)
            json(body(breakfast = "[]", dinner = "[]"))
        }.andExpect { status { isCreated() } }

        val meal = mealRepository.findAll().single()
        assertEquals(emptyList(), meal.breakfast)
        assertEquals(listOf("백미밥", "된장찌개", "잡채"), meal.lunch)
        assertEquals(emptyList(), meal.dinner)
    }

    @Test
    fun `세 끼 중 어느 한 끼만 있어도 등록한다`() {
        listOf(
            body(date = "2026-09-01", lunch = "[]", dinner = "[]"),
            body(date = "2026-09-02", breakfast = "[]", dinner = "[]"),
            body(date = "2026-09-03", breakfast = "[]", lunch = "[]"),
        ).forEach { request ->
            mockMvc.post("/meals") {
                bearer(adminToken)
                json(request)
            }.andExpect { status { isCreated() } }
        }
        assertEquals(3, mealRepository.count())
    }

    @Test
    fun `메뉴는 끼니마다 20개, 하나에 50자까지 받고 따옴표와 역슬래시도 그대로 저장한다`() {
        val menus = (1..20).map { "메뉴$it" }
        val quoted = """콘치즈\"마요\"\\토스트"""
        mockMvc.post("/meals") {
            bearer(adminToken)
            json(body(breakfast = jsonArray(menus), lunch = jsonArray(listOf("가".repeat(50))), dinner = """["$quoted"]"""))
        }.andExpect { status { isCreated() } }

        val meal = mealRepository.findAll().single()
        assertEquals(menus, meal.breakfast)
        assertEquals(listOf("가".repeat(50)), meal.lunch)
        assertEquals(listOf("""콘치즈"마요"\토스트"""), meal.dinner)
    }

    @Test
    fun `같은 날짜에 급식이 있으면 저장을 시도하지 않고 MEAL_ALREADY_EXISTS`() {
        save(date = LocalDate.of(2026, 9, 1))

        mockMvc.post("/meals") {
            bearer(adminToken)
            json(body())
        }.andExpectMealConflict()
        assertEquals(1, mealRepository.count())
        // 중복 검사를 빼도 unique 제약으로 409가 나므로, 검사에서 막혔는지는 저장 호출로 확인한다.
        verify(spiedMealRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `중복 검사를 통과해도 같은 날짜가 먼저 저장됐으면 MEAL_ALREADY_EXISTS`() {
        save(date = LocalDate.of(2026, 9, 1))
        doReturn(null).`when`(spiedMealRepository).findIdByDate(anyDate())

        mockMvc.post("/meals") {
            bearer(adminToken)
            json(body())
        }.andExpectMealConflict()
        assertEquals(1, mealRepository.count())
    }

    @Test
    fun `날짜 제약이 아닌 무결성 오류는 그대로 던진다`() {
        val violation = otherViolation()
        doThrow(violation).`when`(spiedMealRepository).saveAndFlush(any())

        val thrown = assertFailsWith<ServletException> {
            mockMvc.post("/meals") {
                bearer(adminToken)
                json(body())
            }
        }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `형식에 맞지 않는 입력은 INVALID_INPUT`() {
        listOf(
            body(date = "2026-13-01") to "요청 값이 올바르지 않습니다.",
            body(date = "20260901") to "요청 값이 올바르지 않습니다.",
            """{"breakfast":[],"lunch":["백미밥"],"dinner":[]}""" to "요청 값이 올바르지 않습니다.",
            """{"date":"2026-09-01","lunch":["백미밥"],"dinner":[]}""" to "요청 값이 올바르지 않습니다.",
            body(breakfast = "null") to "요청 값이 올바르지 않습니다.",
            body(breakfast = "\"볶음밥\"") to "요청 값이 올바르지 않습니다.",
            body(breakfast = """[" "]""") to "breakfast[0]",
            body(lunch = """["백미밥", null]""") to "요청 값이 올바르지 않습니다.",
            body(dinner = jsonArray(listOf("가".repeat(51)))) to "dinner[0]",
            body(lunch = jsonArray((1..21).map { "메뉴$it" })) to "lunch: ",
            body(breakfast = """["볶음밥\n미역국"]""") to "breakfast[0]",
            body(dinner = """["카레\t라이스"]""") to "dinner[0]",
        ).forEach { (invalid, expectedMessage) ->
            mockMvc.post("/meals") {
                bearer(adminToken)
                json(invalid)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(containsString(expectedMessage)) }
            }
        }
        assertEquals(0, mealRepository.count())
    }

    // 단건 조회

    @Test
    fun `급식을 조회한다`() {
        val meal = save()

        mockMvc.get("/meals/${meal.id}").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(meal.id!!) }
            jsonPath("$.date") { value("2026-09-01") }
            jsonPath("$.breakfast") { value(contains("볶음밥", "미역국", "계란말이")) }
            jsonPath("$.lunch") { value(contains("백미밥", "된장찌개", "잡채")) }
            jsonPath("$.dinner") { value(contains("카레라이스", "김치전")) }
        }
    }

    @Test
    fun `없는 급식이면 MEAL_NOT_FOUND`() {
        mockMvc.get("/meals/999").andExpectMealNotFound()
    }

    @Test
    fun `급식 id가 숫자가 아니면 INVALID_INPUT`() {
        mockMvc.get("/meals/abc").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    // 월간 조회

    @Test
    fun `기간 안의 급식을 날짜 순으로 준다`() {
        save(date = LocalDate.of(2026, 9, 3), lunch = listOf("셋째날"))
        save(date = LocalDate.of(2026, 9, 1), lunch = listOf("첫째날"))
        save(date = LocalDate.of(2026, 8, 31), lunch = listOf("전날"))
        save(date = LocalDate.of(2026, 9, 4), lunch = listOf("다음날"))
        val first = mealRepository.findAll().single { it.date == LocalDate.of(2026, 9, 1) }

        mockMvc.get("/meals?startDate=2026-09-01&finishDate=2026-09-03").andExpect {
            status { isOk() }
            jsonPath("$.startDate") { value("2026-09-01") }
            jsonPath("$.finishDate") { value("2026-09-03") }
            jsonPath("$.meals[*].date") { value(contains("2026-09-01", "2026-09-03")) }
            jsonPath("$.meals[0].id") { value(first.id!!) }
            jsonPath("$.meals[0].breakfast") { value(contains("볶음밥", "미역국", "계란말이")) }
            jsonPath("$.meals[0].lunch") { value(contains("첫째날")) }
            jsonPath("$.meals[0].dinner") { value(contains("카레라이스", "김치전")) }
            jsonPath("$.totalCount") { value(2) }
        }
    }

    @Test
    fun `급식이 없는 기간이면 빈 목록을 준다`() {
        save(date = LocalDate.of(2026, 9, 1))

        mockMvc.get("/meals?startDate=2026-08-01&finishDate=2026-08-31").andExpect {
            status { isOk() }
            jsonPath("$.meals") { isEmpty() }
            jsonPath("$.totalCount") { value(0) }
        }
    }

    @Test
    fun `기간을 생략하면 이번 달을 준다`() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val firstDay = today.withDayOfMonth(1)
        val lastDay = today.withDayOfMonth(today.lengthOfMonth())
        listOf(firstDay.minusDays(1), firstDay, lastDay, lastDay.plusDays(1)).forEach { save(date = it) }

        mockMvc.get("/meals").andExpect {
            status { isOk() }
            jsonPath("$.startDate") { value(firstDay.toString()) }
            jsonPath("$.finishDate") { value(lastDay.toString()) }
            jsonPath("$.meals[*].date") { value(contains(firstDay.toString(), lastDay.toString())) }
            jsonPath("$.totalCount") { value(2) }
        }
    }

    @Test
    fun `하나만 주면 그 날짜가 속한 달의 1일이나 말일로 채운다`() {
        expectRange("startDate=2026-02-10", "2026-02-10", "2026-02-28")
        expectRange("startDate=2028-02-10", "2028-02-10", "2028-02-29")
        expectRange("finishDate=2026-09-20", "2026-09-01", "2026-09-20")
    }

    @Test
    fun `하루만 조회할 수 있고 기간은 92일까지 받는다`() {
        save(date = LocalDate.of(2026, 9, 1))

        mockMvc.get("/meals?startDate=2026-09-01&finishDate=2026-09-01").andExpect {
            status { isOk() }
            jsonPath("$.totalCount") { value(1) }
        }
        expectRange("startDate=2026-09-01&finishDate=2026-12-01", "2026-09-01", "2026-12-01")
    }

    @Test
    fun `종료일이 시작일보다 이르거나 기간이 92일을 넘으면 INVALID_DATE_RANGE`() {
        listOf(
            "startDate=2026-09-02&finishDate=2026-09-01",
            "startDate=2026-09-01&finishDate=2026-12-02",
        ).forEach { query ->
            mockMvc.get("/meals?$query").andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_DATE_RANGE") }
                jsonPath("$.message") { value("조회 기간이 올바르지 않습니다.") }
            }
        }
    }

    @Test
    fun `날짜 형식이 틀리면 INVALID_INPUT`() {
        listOf(
            "startDate=2026-9-1" to "startDate: 형식이 올바르지 않습니다.",
            "finishDate=abc" to "finishDate: 형식이 올바르지 않습니다.",
        ).forEach { (query, expectedMessage) ->
            mockMvc.get("/meals?$query").andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
                jsonPath("$.message") { value(expectedMessage) }
            }
        }
    }

    // 수정

    @Test
    fun `수정하면 모든 값을 바꾸고 이후 나이스 동기화가 덮어쓰지 않게 표시한다`() {
        val meal = save(editedByAdmin = false)

        mockMvc.patch("/meals/${meal.id}") {
            bearer(adminToken)
            json(body(date = "2026-09-02", breakfast = "[]", lunch = """["잡곡밥"]""", dinner = """["짜장면","탕수육"]"""))
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("수정되었습니다.") }
        }

        val updated = mealRepository.findById(meal.id!!).get()
        assertEquals(LocalDate.of(2026, 9, 2), updated.date)
        assertEquals(emptyList(), updated.breakfast)
        assertEquals(listOf("잡곡밥"), updated.lunch)
        assertEquals(listOf("짜장면", "탕수육"), updated.dinner)
        assertTrue(updated.editedByAdmin)
    }

    @Test
    fun `날짜를 그대로 두고 메뉴만 수정할 수 있다`() {
        val meal = save()

        mockMvc.patch("/meals/${meal.id}") {
            bearer(adminToken)
            json(body(lunch = """["잡곡밥"]"""))
        }.andExpect { status { isOk() } }

        assertEquals(listOf("잡곡밥"), mealRepository.findById(meal.id!!).get().lunch)
    }

    @Test
    fun `다른 급식이 있는 날짜로 바꾸면 MEAL_ALREADY_EXISTS`() {
        val meal = save(date = LocalDate.of(2026, 9, 1))
        save(date = LocalDate.of(2026, 9, 2))

        mockMvc.patch("/meals/${meal.id}") {
            bearer(adminToken)
            json(body(date = "2026-09-02"))
        }.andExpectMealConflict()
        assertEquals(LocalDate.of(2026, 9, 1), mealRepository.findById(meal.id!!).get().date)
    }

    @Test
    fun `중복 검사를 통과해도 수정한 날짜가 겹치면 MEAL_ALREADY_EXISTS`() {
        val meal = save(date = LocalDate.of(2026, 9, 1))
        save(date = LocalDate.of(2026, 9, 2))
        doReturn(null).`when`(spiedMealRepository).findByDate(anyDate())

        mockMvc.patch("/meals/${meal.id}") {
            bearer(adminToken)
            json(body(date = "2026-09-02"))
        }.andExpectMealConflict()
        assertEquals(LocalDate.of(2026, 9, 1), mealRepository.findById(meal.id!!).get().date)
    }

    @Test
    fun `수정 중 날짜 제약이 아닌 무결성 오류는 그대로 던진다`() {
        val meal = save()
        val violation = otherViolation()
        doThrow(violation).`when`(spiedMealRepository).flush()

        val thrown = assertFailsWith<ServletException> {
            mockMvc.patch("/meals/${meal.id}") {
                bearer(adminToken)
                json(body())
            }
        }
        assertSame(violation, thrown.cause)
    }

    @Test
    fun `없는 급식을 수정하면 MEAL_NOT_FOUND`() {
        mockMvc.patch("/meals/999") {
            bearer(adminToken)
            json(body())
        }.andExpectMealNotFound()
    }

    @Test
    fun `수정할 값이 형식에 맞지 않으면 INVALID_INPUT`() {
        val meal = save()

        mockMvc.patch("/meals/${meal.id}") {
            bearer(adminToken)
            json(body(lunch = """[" "]"""))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
        assertEquals(listOf("백미밥", "된장찌개", "잡채"), mealRepository.findById(meal.id!!).get().lunch)
    }

    // 급식 없음 표시: 세 끼가 모두 빈 행. 관리자가 나이스 급식을 없앨 때 쓴다.

    @Test
    fun `세 끼를 모두 비워 수정하면 급식 없음으로 바뀌어 조회에서 빠지고 단건 조회는 MEAL_NOT_FOUND`() {
        val meal = save(date = LocalDate.of(2026, 9, 1), editedByAdmin = false)
        save(date = LocalDate.of(2026, 9, 2))

        mockMvc.patch("/meals/${meal.id}") {
            bearer(adminToken)
            json(body(breakfast = "[]", lunch = "[]", dinner = "[]"))
        }.andExpect { status { isOk() } }

        mockMvc.get("/meals/${meal.id}").andExpectMealNotFound()
        mockMvc.get("/meals?startDate=2026-09-01&finishDate=2026-09-30").andExpect {
            status { isOk() }
            jsonPath("$.meals[*].date") { value(contains("2026-09-02")) }
            jsonPath("$.totalCount") { value(1) }
        }
        val marked = mealRepository.findById(meal.id!!).get()
        assertTrue(marked.isNoMeal)
        assertTrue(marked.editedByAdmin)
    }

    @Test
    fun `급식 없음으로 바꾼 날짜는 나이스 동기화가 다시 만들지 않는다`() {
        val meal = save(date = LocalDate.of(2026, 9, 1), editedByAdmin = false)
        mockMvc.patch("/meals/${meal.id}") {
            bearer(adminToken)
            json(body(breakfast = "[]", lunch = "[]", dinner = "[]"))
        }.andExpect { status { isOk() } }

        val result = syncNeisMealsUseCase.execute(listOf(neisMeal(LocalDate.of(2026, 9, 1))))

        assertEquals(MealSyncResult(created = 0, updated = 0, skipped = 1), result)
        assertEquals(1, mealRepository.count())
        assertTrue(mealRepository.findById(meal.id!!).get().isNoMeal)
        mockMvc.get("/meals?startDate=2026-09-01&finishDate=2026-09-30").andExpect { jsonPath("$.totalCount") { value(0) } }
    }

    @Test
    fun `날짜를 옮기면 원래 날짜에 급식 없음 표시를 남겨 나이스 동기화가 다시 만들지 않는다`() {
        val meal = save(date = LocalDate.of(2026, 9, 2), editedByAdmin = false)

        mockMvc.patch("/meals/${meal.id}") {
            bearer(adminToken)
            json(body(date = "2026-09-01"))
        }.andExpect { status { isOk() } }
        val result = syncNeisMealsUseCase.execute(listOf(neisMeal(LocalDate.of(2026, 9, 2))))

        assertEquals(MealSyncResult(created = 0, updated = 0, skipped = 1), result)
        val marker = mealRepository.findAll().single { it.id != meal.id }
        assertEquals(LocalDate.of(2026, 9, 2), marker.date)
        assertTrue(marker.isNoMeal)
        assertTrue(marker.editedByAdmin)
        mockMvc.get("/meals?startDate=2026-09-01&finishDate=2026-09-30").andExpect {
            jsonPath("$.meals[*].id") { value(contains(meal.id!!.toInt())) }
            jsonPath("$.meals[0].date") { value("2026-09-01") }
            jsonPath("$.totalCount") { value(1) }
        }
    }

    @Test
    fun `세 끼를 모두 비워 등록하면 급식 없음으로 미리 막아 두고 조회에서는 빠진다`() {
        mockMvc.post("/meals") {
            bearer(adminToken)
            json(body(breakfast = "[]", lunch = "[]", dinner = "[]"))
        }.andExpect { status { isCreated() } }

        val marker = mealRepository.findAll().single()
        assertTrue(marker.isNoMeal)
        assertTrue(marker.editedByAdmin)
        mockMvc.get("/meals/${marker.id}").andExpectMealNotFound()
        mockMvc.get("/meals?startDate=2026-09-01&finishDate=2026-09-30").andExpect { jsonPath("$.totalCount") { value(0) } }
        assertEquals(
            MealSyncResult(created = 0, updated = 0, skipped = 1),
            syncNeisMealsUseCase.execute(listOf(neisMeal(LocalDate.of(2026, 9, 1)))),
        )
    }

    @Test
    fun `급식 없음 날짜에 등록하면 그 표시를 새 급식으로 바꾼다`() {
        val marker = mealRepository.save(Meal.noMeal(LocalDate.of(2026, 9, 1)))

        mockMvc.post("/meals") {
            bearer(adminToken)
            json(body())
        }.andExpect {
            status { isCreated() }
            header { string("Location", "/meals/${marker.id}") }
        }

        val meal = mealRepository.findAll().single()
        assertEquals(marker.id, meal.id)
        assertEquals(listOf("볶음밥", "미역국", "계란말이"), meal.breakfast)
        assertEquals(listOf("백미밥", "된장찌개", "잡채"), meal.lunch)
        assertEquals(listOf("카레라이스", "김치전"), meal.dinner)
        assertTrue(meal.editedByAdmin)
        mockMvc.get("/meals/${marker.id}").andExpect { status { isOk() } }
    }

    @Test
    fun `급식 없음 날짜로 옮길 수 있다`() {
        val meal = save(date = LocalDate.of(2026, 9, 1))
        val marker = mealRepository.save(Meal.noMeal(LocalDate.of(2026, 9, 2)))

        mockMvc.patch("/meals/${meal.id}") {
            bearer(adminToken)
            json(body(date = "2026-09-02"))
        }.andExpect { status { isOk() } }

        assertFalse(mealRepository.existsById(marker.id!!))
        val meals = mealRepository.findAll().sortedBy(Meal::date)
        assertEquals(listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)), meals.map(Meal::date))
        assertTrue(meals[0].isNoMeal)
        assertEquals(meal.id, meals[1].id)
        assertEquals(listOf("백미밥", "된장찌개", "잡채"), meals[1].lunch)
    }

    @Test
    fun `급식 없음 표시는 수정할 수 없어 MEAL_NOT_FOUND`() {
        val marker = mealRepository.save(Meal.noMeal(LocalDate.of(2026, 9, 1)))

        mockMvc.patch("/meals/${marker.id}") {
            bearer(adminToken)
            json(body())
        }.andExpectMealNotFound()
        assertTrue(mealRepository.findById(marker.id!!).get().isNoMeal)
    }

    // 권한

    @Test
    fun `조회는 토큰이 없거나 유효하지 않아도 된다`() {
        val meal = save()

        listOf(null, "invalid-token", userToken, adminToken).forEach { token ->
            mockMvc.get("/meals?startDate=2026-09-01&finishDate=2026-09-30") {
                token?.let { bearer(it) }
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalCount") { value(1) }
            }
            mockMvc.get("/meals/${meal.id}") {
                token?.let { bearer(it) }
            }.andExpect { status { isOk() } }
        }
    }

    @Test
    fun `토큰 없이 등록, 수정하면 INVALID_TOKEN`() {
        val meal = save()

        listOf(
            mockMvc.post("/meals") { json(body(date = "2026-09-02")) },
            mockMvc.patch("/meals/${meal.id}") { json(body()) },
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_TOKEN") }
            }
        }
    }

    @Test
    fun `USER는 등록, 수정을 할 수 없다`() {
        val meal = save()

        listOf(
            mockMvc.post("/meals") { bearer(userToken); json(body(date = "2026-09-02")) },
            mockMvc.patch("/meals/${meal.id}") { bearer(userToken); json(body(lunch = """["바뀜"]""")) },
        ).forEach {
            it.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
        }
        val saved = mealRepository.findAll().single()
        assertEquals(listOf("백미밥", "된장찌개", "잡채"), saved.lunch)
        assertFalse(saved.date == LocalDate.of(2026, 9, 2))
    }

    private fun save(
        date: LocalDate = LocalDate.of(2026, 9, 1),
        breakfast: List<String> = listOf("볶음밥", "미역국", "계란말이"),
        lunch: List<String> = listOf("백미밥", "된장찌개", "잡채"),
        dinner: List<String> = listOf("카레라이스", "김치전"),
        editedByAdmin: Boolean = true,
    ) = mealRepository.save(Meal(date, breakfast, lunch, dinner, editedByAdmin))

    private fun neisMeal(date: LocalDate) = NeisMeal(date, listOf("죽"), listOf("나이스밥"), listOf("카레"))

    private fun expectRange(query: String, startDate: String, finishDate: String) {
        mockMvc.get("/meals?$query").andExpect {
            status { isOk() }
            jsonPath("$.startDate") { value(startDate) }
            jsonPath("$.finishDate") { value(finishDate) }
        }
    }

    // findByDate·findIdByDate는 Kotlin에서 LocalDate(non-null)로 선언돼 any()의 null을 받지 못한다. 매처를 등록한 뒤 null 대신 아무 날짜나 넘긴다.
    private fun anyDate(): LocalDate = any(LocalDate::class.java) ?: LocalDate.MIN

    private fun otherViolation() =
        DataIntegrityViolationException("other", ConstraintViolationException("other", SQLException(), "other_constraint"))

    private fun ResultActionsDsl.andExpectMealNotFound() = andExpect {
        status { isNotFound() }
        jsonPath("$.code") { value("MEAL_NOT_FOUND") }
        jsonPath("$.message") { value("존재하지 않는 급식 정보입니다.") }
    }

    private fun ResultActionsDsl.andExpectMealConflict() = andExpect {
        status { isConflict() }
        jsonPath("$.code") { value("MEAL_ALREADY_EXISTS") }
        jsonPath("$.message") { value("해당 날짜의 급식 정보가 이미 존재합니다.") }
    }

    private fun MockHttpServletRequestDsl.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun jsonArray(menus: List<String>) = menus.joinToString(",", "[", "]") { "\"$it\"" }

    private fun body(
        date: String = "2026-09-01",
        breakfast: String = """["볶음밥","미역국","계란말이"]""",
        lunch: String = """["백미밥","된장찌개","잡채"]""",
        dinner: String = """["카레라이스","김치전"]""",
    ) = """
        {
          "date": "$date",
          "breakfast": $breakfast,
          "lunch": $lunch,
          "dinner": $dinner
        }
    """.trimIndent()
}
