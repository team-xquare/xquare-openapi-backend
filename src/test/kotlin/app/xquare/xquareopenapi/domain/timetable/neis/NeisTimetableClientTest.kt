package app.xquare.xquareopenapi.domain.timetable.neis

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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NeisTimetableClientTest {
    private val builder = RestClient.builder().baseUrl("https://open.neis.go.kr")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = NeisTimetableClient(builder.build(), NeisProperties("test-key", "G10", "7430310"))

    private val monday = LocalDate.of(2026, 11, 16)
    private val sunday = LocalDate.of(2026, 11, 22)

    @Test
    fun `학급과 기간을 학교 코드, 키와 함께 요청한다`() {
        server.expect(requestTo(startsWith("https://open.neis.go.kr/hub/hisTimetable?")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("KEY", "test-key"))
            .andExpect(queryParam("Type", "json"))
            .andExpect(queryParam("pIndex", "1"))
            .andExpect(queryParam("pSize", "100"))
            .andExpect(queryParam("ATPT_OFCDC_SC_CODE", "G10"))
            .andExpect(queryParam("SD_SCHUL_CODE", "7430310"))
            .andExpect(queryParam("GRADE", "2"))
            .andExpect(queryParam("CLASS_NM", "3"))
            .andExpect(queryParam("TI_FROM_YMD", "20261116"))
            .andExpect(queryParam("TI_TO_YMD", "20261122"))
            .andRespond(withSuccess(NO_DATA, MediaType.APPLICATION_JSON))

        client.fetch(2, 3, monday, sunday)

        server.verify()
    }

    @Test
    fun `실제 응답을 날짜, 교시, 과목으로 바꾼다`() {
        // 나이스가 실제로 준 응답이다(2026-11-16 1학년 1반, 키 없이 받은 샘플 5건). 키가 없어 list_total_count가 33이었던 것만 5로 고쳤다.
        respond(javaClass.getResource("/neis/his-timetable.json")!!.readText())

        assertEquals(
            listOf(
                NeisTimetable(monday, 1, "자율·자치활동"),
                NeisTimetable(monday, 2, "공통수학2"),
                NeisTimetable(monday, 3, "웹 개발 입문"),
                NeisTimetable(monday, 4, "웹 개발 입문"),
                NeisTimetable(monday, 5, "통합과학2"),
            ),
            client.fetch(1, 1, monday, sunday),
        )
    }

    @Test
    fun `날짜, 교시 순으로 정렬하고 교시는 숫자로 비교하며 과목의 앞뒤 공백을 지운다`() {
        respond(rows(row("20261117", "1", "국어"), row("20261116", "10", "방과후"), row("20261116", "2", " 수학 ")))

        assertEquals(
            listOf(
                NeisTimetable(monday, 2, "수학"),
                NeisTimetable(monday, 10, "방과후"),
                NeisTimetable(LocalDate.of(2026, 11, 17), 1, "국어"),
            ),
            client.fetch(1, 1, monday, sunday),
        )
    }

    @Test
    fun `같은 날짜, 같은 교시가 여러 행이면 모두 나이스가 준 순서대로 남긴다`() {
        // 선택 과목처럼 한 교시에 수업이 둘 이상 올 수 있다.
        respond(rows(row("20261116", "3", "물리학"), row("20261116", "1", "국어"), row("20261116", "3", "화학")))

        assertEquals(
            listOf(NeisTimetable(monday, 1, "국어"), NeisTimetable(monday, 3, "물리학"), NeisTimetable(monday, 3, "화학")),
            client.fetch(1, 1, monday, sunday),
        )
    }

    @Test
    fun `데이터가 없으면(INFO-200) 빈 목록을 준다`() {
        respond(NO_DATA)

        assertEquals(emptyList(), client.fetch(1, 1, monday, sunday))
    }

    @Test
    fun `그 밖의 결과 코드면 NeisApiException`() {
        respond("""{"RESULT":{"CODE":"ERROR-290","MESSAGE":"인증키가 유효하지 않습니다."}}""")

        val thrown = assertFailsWith<NeisApiException> { client.fetch(1, 1, monday, sunday) }
        assertEquals("ERROR-290 인증키가 유효하지 않습니다.", thrown.message)
    }

    @Test
    fun `결과 코드도 시간표도 없는 응답이면 NeisApiException`() {
        listOf(
            "{}",
            """{"hisTimetable":[]}""",
            // head는 정상인데 row가 없다.
            """{"hisTimetable":[{"head":[{"list_total_count":0},{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."}}]}]}""",
            // row는 있는데 head의 결과 코드가 없다.
            """{"hisTimetable":[{"head":[{"list_total_count":1}]},{"row":[${row("20261116", "1", "국어")}]}]}""",
            """{"hisTimetable":[{"head":[{"list_total_count":1},{"RESULT":{"CODE":"INFO-000"}}]},{"row":{}}]}""",
        ).forEach { body ->
            server.reset()
            respond(body)

            val thrown = assertFailsWith<NeisApiException>(body) { client.fetch(1, 1, monday, sunday) }
            assertEquals("UNEXPECTED_RESPONSE 나이스 시간표 응답 형식이 예상과 다릅니다.", thrown.message)
        }
    }

    @Test
    fun `head의 결과 코드가 INFO-000이 아니면 NeisApiException`() {
        respond(
            """{"hisTimetable":[{"head":[{"list_total_count":1},{"RESULT":{"CODE":"INFO-300","MESSAGE":"관리자에 의해 인증키 사용이 제한되었습니다."}}]},""" +
                """{"row":[${row("20261116", "1", "국어")}]}]}""",
        )

        val thrown = assertFailsWith<NeisApiException> { client.fetch(1, 1, monday, sunday) }
        assertEquals("INFO-300 관리자에 의해 인증키 사용이 제한되었습니다.", thrown.message)
    }

    @Test
    fun `전체 건수보다 적게 받으면 NeisApiException이고, 같으면 정상이다`() {
        respond(rows(row("20261116", "1", "국어"), totalCount = 2))

        val thrown = assertFailsWith<NeisApiException> { client.fetch(1, 1, monday, sunday) }
        assertEquals("UNEXPECTED_RESPONSE 나이스 시간표를 일부만 받았습니다.", thrown.message)

        server.reset()
        respond(rows(row("20261116", "1", "국어"), totalCount = 1))
        assertEquals(1, client.fetch(1, 1, monday, sunday).size)
    }

    @Test
    fun `전체 건수가 없거나 null이거나 정수가 아니면 NeisApiException`() {
        // asInt()로 읽으면 모두 0이 되어 일부만 받은 응답도 통과하던 경우다.
        listOf("{}", """{"list_total_count":null}""", """{"list_total_count":"1"}""", """{"list_total_count":1.5}""")
            .forEach { countHead ->
                server.reset()
                respond(
                    """{"hisTimetable":[{"head":[$countHead,{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."}}]},""" +
                        """{"row":[${row("20261116", "1", "국어")}]}]}""",
                )

                val thrown = assertFailsWith<NeisApiException>(countHead) { client.fetch(1, 1, monday, sunday) }
                assertEquals("UNEXPECTED_RESPONSE 나이스 시간표 응답 형식이 예상과 다릅니다.", thrown.message)
            }
    }

    @Test
    fun `행의 날짜, 교시, 과목이 없거나 비었거나 읽을 수 없으면 NeisApiException`() {
        val valid = mapOf("ALL_TI_YMD" to "\"20261116\"", "PERIO" to "\"1\"", "ITRT_CNTNT" to "\"국어\"")
        val broken = listOf(null, "null", "\"\"", "\"  \"", "1")
        val cases = valid.keys.flatMap { field -> broken.map { field to it } } +
            listOf("ALL_TI_YMD" to "\"2026-11-16\"", "ALL_TI_YMD" to "\"20261132\"", "PERIO" to "\"1교시\"")
        cases.forEach { (field, value) ->
            val fields = valid.toMutableMap()
            if (value == null) fields.remove(field) else fields[field] = value
            val brokenRow = fields.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }
            server.reset()
            // 정상 행과 섞여 있어도 응답 전체를 실패로 본다.
            respond(rows(row("20261116", "2", "수학"), brokenRow))

            val thrown = assertFailsWith<NeisApiException>(brokenRow) { client.fetch(1, 1, monday, sunday) }
            assertEquals("UNEXPECTED_RESPONSE 나이스 시간표 행에 날짜, 교시, 과목이 올바르지 않습니다.", thrown.message)
        }
    }

    @Test
    fun `행이 객체가 아니면 NeisApiException`() {
        respond(rows("null"))

        val thrown = assertFailsWith<NeisApiException> { client.fetch(1, 1, monday, sunday) }
        assertEquals("UNEXPECTED_RESPONSE 나이스 시간표 행에 날짜, 교시, 과목이 올바르지 않습니다.", thrown.message)
    }

    @Test
    fun `HTTP 오류는 그대로 던진다`() {
        server.expect(requestTo(startsWith("https://open.neis.go.kr/hub/hisTimetable?")))
            .andRespond(withServerError())

        assertFailsWith<HttpServerErrorException> { client.fetch(1, 1, monday, sunday) }
    }

    private fun respond(body: String) {
        server.expect(requestTo(startsWith("https://open.neis.go.kr/hub/hisTimetable?")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    companion object {
        const val NO_DATA = """{"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}"""

        fun row(date: String, period: String, subject: String) =
            """{"ALL_TI_YMD":"$date","PERIO":"$period","ITRT_CNTNT":"$subject"}"""

        fun rows(vararg rows: String, totalCount: Int = rows.size) =
            """{"hisTimetable":[{"head":[{"list_total_count":$totalCount},{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."}}]},{"row":[${rows.joinToString(",")}]}]}"""
    }
}
