package app.xquare.xquareopenapi.domain.meal.neis

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NeisMealClientTest {
    private val builder = RestClient.builder().baseUrl("https://open.neis.go.kr")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = NeisMealClient(builder.build(), NeisProperties("test-key", "G10", "7430310"))

    @Test
    fun `한 달 치를 학교 코드와 키로 요청한다`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://open.neis.go.kr/hub/mealServiceDietInfo?")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("KEY", "test-key"))
            .andExpect(queryParam("Type", "json"))
            .andExpect(queryParam("pIndex", "1"))
            .andExpect(queryParam("pSize", "100"))
            .andExpect(queryParam("ATPT_OFCDC_SC_CODE", "G10"))
            .andExpect(queryParam("SD_SCHUL_CODE", "7430310"))
            .andExpect(queryParam("MLSV_FROM_YMD", "20260201"))
            .andExpect(queryParam("MLSV_TO_YMD", "20260228"))
            .andRespond(withSuccess(NO_DATA, MediaType.APPLICATION_JSON))

        client.fetchMonth(YearMonth.of(2026, 2))

        server.verify()
    }

    @Test
    fun `실제 응답을 날짜별 조식, 중식, 석식으로 묶는다`() {
        // 나이스가 실제로 준 응답이다(2026-10-01~02, 키 없이 받은 샘플 5건). 끼니 순으로 와서 날짜가 섞여 있다.
        respond(javaClass.getResource("/neis/meal-service-diet-info.json")!!.readText())

        val meals = client.fetchMonth(YearMonth.of(2026, 10))

        assertEquals(
            listOf(
                NeisMeal(
                    LocalDate.of(2026, 10, 1),
                    breakfast = listOf("진미채돌자반주먹밥", "배추김치", "케이준치킨샐러드", "콘치즈마요토스트/연유", "아몬드후레이크/오레오오즈"),
                    lunch = listOf("불고기솥밥", "순두부계란탕", "야채계란찜", "불향쭈꾸미볶음/깻잎", "배추김치", "초코크림카스테라"),
                    dinner = listOf("잡곡밥", "얼갈이된장국", "단호박돈사태떡찜", "오지치즈후라이", "제로복숭아아이스티", "열무김치"),
                ),
                NeisMeal(
                    LocalDate.of(2026, 10, 2),
                    breakfast = listOf("참치김치볶음밥", "우동장국", "팝콘만두/칠리소스", "단무지", "초코라떼"),
                    lunch = listOf("기장밥", "돈육고추장찌개", "카프레제샐러드", "마라로제오리불고기", "총각김치"),
                    dinner = emptyList(),
                ),
            ),
            meals,
        )
    }

    @Test
    fun `조식, 중식, 석식이 아닌 끼니는 버리고 그것만 있는 날은 뺀다`() {
        respond(rows(row("20261007", "4", "간식 (2)"), row("20261006", "4", "간식 (2)"), row("20261006", "3", "카레 (5)")))

        assertEquals(
            listOf(NeisMeal(LocalDate.of(2026, 10, 6), emptyList(), emptyList(), listOf("카레"))),
            client.fetchMonth(YearMonth.of(2026, 10)),
        )
    }

    @Test
    fun `한 끼만 있는 날도 급식이 있는 날로 보고 날짜 순으로 준다`() {
        // 나이스는 끼니 순으로 줘서 처음 나온 날짜 순서가 날짜 순과 다를 수 있다.
        respond(rows(row("20261009", "1", "죽"), row("20261008", "2", "백미밥"), row("20261007", "3", "카레")))

        assertEquals(
            listOf(
                NeisMeal(LocalDate.of(2026, 10, 7), emptyList(), emptyList(), listOf("카레")),
                NeisMeal(LocalDate.of(2026, 10, 8), emptyList(), listOf("백미밥"), emptyList()),
                NeisMeal(LocalDate.of(2026, 10, 9), listOf("죽"), emptyList(), emptyList()),
            ),
            client.fetchMonth(YearMonth.of(2026, 10)),
        )
    }

    @Test
    fun `데이터가 없으면(INFO-200) 빈 목록을 준다`() {
        respond(NO_DATA)

        assertEquals(emptyList(), client.fetchMonth(YearMonth.of(2030, 1)))
    }

    @Test
    fun `그 밖의 결과 코드면 NeisApiException`() {
        respond("""{"RESULT":{"CODE":"ERROR-290","MESSAGE":"인증키가 유효하지 않습니다."}}""")

        val thrown = assertFailsWith<NeisApiException> { client.fetchMonth(YearMonth.of(2026, 10)) }
        assertEquals("ERROR-290 인증키가 유효하지 않습니다.", thrown.message)
    }

    @Test
    fun `HTTP 오류는 그대로 던진다`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://open.neis.go.kr/hub/mealServiceDietInfo?")))
            .andRespond(withServerError())

        assertFailsWith<HttpServerErrorException> { client.fetchMonth(YearMonth.of(2026, 10)) }
    }

    private fun respond(body: String) {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://open.neis.go.kr/hub/mealServiceDietInfo?")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    companion object {
        const val NO_DATA = """{"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}"""

        fun row(date: String, mealCode: String, dishes: String) =
            """{"MMEAL_SC_CODE":"$mealCode","MLSV_YMD":"$date","DDISH_NM":"$dishes"}"""

        fun rows(vararg rows: String) =
            """{"mealServiceDietInfo":[{"head":[{"list_total_count":${rows.size}},{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."}}]},{"row":[${rows.joinToString(",")}]}]}"""
    }
}
