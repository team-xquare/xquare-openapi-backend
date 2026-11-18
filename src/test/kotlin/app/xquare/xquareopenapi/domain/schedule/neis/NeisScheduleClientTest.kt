package app.xquare.xquareopenapi.domain.schedule.neis

import app.xquare.xquareopenapi.domain.meal.neis.NeisApiException
import app.xquare.xquareopenapi.domain.meal.neis.NeisProperties
import org.hamcrest.Matchers.startsWith
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

class NeisScheduleClientTest {
    private val builder = RestClient.builder().baseUrl("https://open.neis.go.kr")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = NeisScheduleClient(builder.build(), NeisProperties("test-key", "G10", "7430310"))

    @Test
    fun `한 달 치를 학교 코드와 키로 요청한다`() {
        server.expect(requestTo(startsWith("https://open.neis.go.kr/hub/SchoolSchedule?")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("KEY", "test-key"))
            .andExpect(queryParam("Type", "json"))
            .andExpect(queryParam("pIndex", "1"))
            .andExpect(queryParam("pSize", "100"))
            .andExpect(queryParam("ATPT_OFCDC_SC_CODE", "G10"))
            .andExpect(queryParam("SD_SCHUL_CODE", "7430310"))
            .andExpect(queryParam("AA_FROM_YMD", "20260201"))
            .andExpect(queryParam("AA_TO_YMD", "20260228"))
            .andRespond(withSuccess(NO_DATA, MediaType.APPLICATION_JSON))

        client.fetchMonth(YearMonth.of(2026, 2))

        server.verify()
    }

    @Test
    fun `실제 응답을 날짜, 제목, 내용으로 바꾼다`() {
        // 나이스가 실제로 준 응답이다(2026년 3월, 키 없이 받은 샘플 5건). 키가 없어 list_total_count가 15였던 것만 5로 고쳤다.
        respond(javaClass.getResource("/neis/school-schedule.json")!!.readText())

        assertEquals(
            listOf(
                NeisSchedule(LocalDate.of(2026, 3, 1), "3·1절", null),
                NeisSchedule(LocalDate.of(2026, 3, 2), "대체공휴일", null),
                NeisSchedule(LocalDate.of(2026, 3, 3), "입학식", null),
                NeisSchedule(LocalDate.of(2026, 3, 3), "개학식", null),
                NeisSchedule(LocalDate.of(2026, 3, 6), "의무귀가", null),
            ),
            client.fetchMonth(YearMonth.of(2026, 3)),
        )
    }

    @Test
    fun `제목과 내용의 앞뒤 공백을 지우고, 빈 내용은 null, 제목이 빈 일정은 뺀다`() {
        respond(rows(row("20260305", " 체육대회 ", " 우천 시 순연 "), row("20260306", "  ", "내용만"), row("20260307", "현장체험학습", "  ")))

        assertEquals(
            listOf(
                NeisSchedule(LocalDate.of(2026, 3, 5), "체육대회", "우천 시 순연"),
                NeisSchedule(LocalDate.of(2026, 3, 7), "현장체험학습", null),
            ),
            client.fetchMonth(YearMonth.of(2026, 3)),
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

        val thrown = assertFailsWith<NeisApiException> { client.fetchMonth(YearMonth.of(2026, 3)) }
        assertEquals("ERROR-290 인증키가 유효하지 않습니다.", thrown.message)
    }

    @Test
    fun `결과 코드도 일정 목록도 없는 응답이면 NeisApiException`() {
        listOf(
            "{}",
            """{"SchoolSchedule":[]}""",
            // head는 정상인데 row가 없다.
            """{"SchoolSchedule":[{"head":[{"list_total_count":0},{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."}}]}]}""",
            // row는 있는데 head의 결과 코드가 없다.
            """{"SchoolSchedule":[{"head":[{"list_total_count":1}]},{"row":[${row("20260305", "체육대회", "")}]}]}""",
            """{"SchoolSchedule":[{"head":[{"list_total_count":1},{"RESULT":{"CODE":"INFO-000"}}]},{"row":{}}]}""",
        ).forEach { body ->
            server.reset()
            respond(body)

            val thrown = assertFailsWith<NeisApiException>(body) { client.fetchMonth(YearMonth.of(2026, 3)) }
            assertEquals("UNEXPECTED_RESPONSE 나이스 학사일정 응답 형식이 예상과 다릅니다.", thrown.message)
        }
    }

    @Test
    fun `head의 결과 코드가 INFO-000이 아니면 NeisApiException`() {
        respond(
            """{"SchoolSchedule":[{"head":[{"list_total_count":1},{"RESULT":{"CODE":"INFO-300","MESSAGE":"관리자에 의해 인증키 사용이 제한되었습니다."}}]},""" +
                """{"row":[${row("20260305", "체육대회", "")}]}]}""",
        )

        val thrown = assertFailsWith<NeisApiException> { client.fetchMonth(YearMonth.of(2026, 3)) }
        assertEquals("INFO-300 관리자에 의해 인증키 사용이 제한되었습니다.", thrown.message)
    }

    @Test
    fun `전체 건수보다 적게 받으면 NeisApiException이고, 같으면 정상이다`() {
        // 동기화는 받은 목록에 없는 나이스 일정을 지우므로 일부만 받은 목록을 정상으로 돌려주면 안 된다.
        respond(rows(row("20260305", "체육대회", ""), totalCount = 2))

        val thrown = assertFailsWith<NeisApiException> { client.fetchMonth(YearMonth.of(2026, 3)) }
        assertEquals("UNEXPECTED_RESPONSE 나이스 학사일정을 일부만 받았습니다.", thrown.message)

        server.reset()
        respond(rows(row("20260305", "체육대회", ""), totalCount = 1))
        assertEquals(1, client.fetchMonth(YearMonth.of(2026, 3)).size)
    }

    @Test
    fun `HTTP 오류는 그대로 던진다`() {
        server.expect(requestTo(startsWith("https://open.neis.go.kr/hub/SchoolSchedule?")))
            .andRespond(withServerError())

        assertFailsWith<HttpServerErrorException> { client.fetchMonth(YearMonth.of(2026, 3)) }
    }

    private fun respond(body: String) {
        server.expect(requestTo(startsWith("https://open.neis.go.kr/hub/SchoolSchedule?")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    companion object {
        const val NO_DATA = """{"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}"""

        fun row(date: String, title: String, content: String) =
            """{"AA_YMD":"$date","EVENT_NM":"$title","EVENT_CNTNT":"$content"}"""

        fun rows(vararg rows: String, totalCount: Int = rows.size) =
            """{"SchoolSchedule":[{"head":[{"list_total_count":$totalCount},{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."}}]},{"row":[${rows.joinToString(",")}]}]}"""
    }
}
