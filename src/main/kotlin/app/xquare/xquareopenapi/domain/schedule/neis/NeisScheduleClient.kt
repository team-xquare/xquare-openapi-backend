package app.xquare.xquareopenapi.domain.schedule.neis

import app.xquare.xquareopenapi.domain.meal.neis.NeisApiException
import app.xquare.xquareopenapi.domain.meal.neis.NeisProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// 나이스 학사일정(SchoolSchedule)을 월 단위로 가져온다. 학교 코드·인증키·RestClient는 급식 동기화와 같은 것을 쓴다.
@Component
class NeisScheduleClient(
    private val neisRestClient: RestClient,
    private val neisProperties: NeisProperties,
) {

    // 그 달의 학사일정을 나이스가 준 순서대로 준다. 데이터가 없으면(INFO-200) 빈 목록이다.
    // 그 밖의 결과 코드, 예상과 다른 응답, 전체 건수(list_total_count)가 없거나 받은 건수와 다른 응답이면 NeisApiException을 던진다.
    // 동기화가 이 목록에 없는 나이스 일정을 지우므로, 덜 받은 목록을 정상으로 돌려주면 안 된다.
    fun fetchMonth(month: YearMonth): List<NeisSchedule> {
        val response = neisRestClient.get()
            .uri {
                it.path(PATH)
                    .queryParam("KEY", neisProperties.apiKey)
                    .queryParam("Type", "json")
                    .queryParam("pIndex", 1)
                    .queryParam("pSize", PAGE_SIZE)
                    .queryParam("ATPT_OFCDC_SC_CODE", neisProperties.officeCode)
                    .queryParam("SD_SCHUL_CODE", neisProperties.schoolCode)
                    .queryParam("AA_FROM_YMD", month.atDay(1).format(DATE_FORMAT))
                    .queryParam("AA_TO_YMD", month.atEndOfMonth().format(DATE_FORMAT))
                    .build()
            }
            .retrieve()
            .body<JsonNode>()!!
        // 데이터가 없거나 오류면 {"RESULT":{"CODE":..., "MESSAGE":...}}만 온다.
        val result = response.path("RESULT")
        if (!result.isMissingNode) {
            val code = result.path("CODE").asString()
            if (code == NO_DATA) return emptyList()
            throw NeisApiException(code, result.path("MESSAGE").asString())
        }
        // 정상이면 {"SchoolSchedule":[{"head":[{"list_total_count":N},{"RESULT":{...}}]},{"row":[...]}]}로 온다.
        val service = response.path(SERVICE)
        val head = service.path(0).path("head")
        val headResult = head.path(1).path("RESULT")
        val rows = service.path(1).path("row")
        if (headResult.isMissingNode || !rows.isArray) {
            throw NeisApiException(UNEXPECTED_RESPONSE, "나이스 학사일정 응답 형식이 예상과 다릅니다.")
        }
        val code = headResult.path("CODE").asString()
        if (code != OK) throw NeisApiException(code, headResult.path("MESSAGE").asString())
        // asInt()는 누락·null·문자열·소수를 0이나 잘린 값으로 바꿔 검사를 통과시키므로, int 범위의 정수인지 먼저 본다.
        val totalCount = head.path(0).path("list_total_count")
        if (!totalCount.isIntegralNumber || !totalCount.canConvertToInt()) {
            throw NeisApiException(UNEXPECTED_RESPONSE, "나이스 학사일정 전체 건수가 없거나 정수가 아닙니다.")
        }
        if (totalCount.intValue() != rows.size()) {
            throw NeisApiException(UNEXPECTED_RESPONSE, "나이스 학사일정 전체 건수와 받은 건수가 다릅니다.")
        }
        return rows.values()
            .map {
                NeisSchedule(
                    date = LocalDate.parse(it.path("AA_YMD").asString(), DATE_FORMAT),
                    title = it.path("EVENT_NM").asString().trim(),
                    content = it.path("EVENT_CNTNT").asString().trim().ifEmpty { null },
                )
            }
            // 제목 없는 일정은 응답에서 title이 필수라 저장하지 않는다.
            .filter { it.title.isNotEmpty() }
    }

    companion object {
        private const val PATH = "/hub/SchoolSchedule"
        private const val SERVICE = "SchoolSchedule"
        private const val OK = "INFO-000"
        private const val NO_DATA = "INFO-200"
        private const val UNEXPECTED_RESPONSE = "UNEXPECTED_RESPONSE"

        // 한 달 학사일정은 많아야 수십 건이라 한 번에 다 받는다. 넘으면 list_total_count 검사에 걸려 그 달 동기화가 실패로 남는다.
        // 키 없이 부르면 나이스가 샘플 5건만 주는데(list_total_count는 실제 건수), 이것도 같은 검사에 걸린다.
        private const val PAGE_SIZE = 100
        private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
    }
}
