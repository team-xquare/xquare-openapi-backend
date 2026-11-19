package app.xquare.xquareopenapi.domain.timetable.neis

import app.xquare.xquareopenapi.domain.meal.neis.NeisApiException
import app.xquare.xquareopenapi.domain.meal.neis.NeisProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// 나이스 고등학교시간표(hisTimetable)를 가져온다. 학교 코드·인증키·RestClient는 급식 동기화와 같은 것을 쓴다.
@Component
class NeisTimetableClient(
    private val neisRestClient: RestClient,
    private val neisProperties: NeisProperties,
) {

    // 학급 하나의 기간 시간표를 날짜·교시 순으로 준다. 데이터가 없으면(INFO-200) 빈 목록이다.
    // 그 밖의 결과 코드, 예상과 다른 응답, 일부만 받은 응답이면 NeisApiException을 던진다.
    fun fetch(grade: Int, classNum: Int, startDate: LocalDate, finishDate: LocalDate): List<NeisTimetable> {
        val response = neisRestClient.get()
            .uri {
                it.path(PATH)
                    .queryParam("KEY", neisProperties.apiKey)
                    .queryParam("Type", "json")
                    .queryParam("pIndex", 1)
                    .queryParam("pSize", PAGE_SIZE)
                    .queryParam("ATPT_OFCDC_SC_CODE", neisProperties.officeCode)
                    .queryParam("SD_SCHUL_CODE", neisProperties.schoolCode)
                    .queryParam("GRADE", grade)
                    .queryParam("CLASS_NM", classNum)
                    .queryParam("TI_FROM_YMD", startDate.format(DATE_FORMAT))
                    .queryParam("TI_TO_YMD", finishDate.format(DATE_FORMAT))
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
        // 정상이면 {"hisTimetable":[{"head":[{"list_total_count":N},{"RESULT":{...}}]},{"row":[...]}]}로 온다.
        val service = response.path(SERVICE)
        val head = service.path(0).path("head")
        val headResult = head.path(1).path("RESULT")
        val rows = service.path(1).path("row")
        if (headResult.isMissingNode || !rows.isArray) {
            throw NeisApiException(UNEXPECTED_RESPONSE, "나이스 시간표 응답 형식이 예상과 다릅니다.")
        }
        val code = headResult.path("CODE").asString()
        if (code != OK) throw NeisApiException(code, headResult.path("MESSAGE").asString())
        // 건수가 없거나 정수가 아니면 asInt()가 0을 줘서 아래 검사를 그냥 통과하므로 형식부터 확인한다.
        val totalCount = head.path(0).path("list_total_count")
        if (!totalCount.isInt) {
            throw NeisApiException(UNEXPECTED_RESPONSE, "나이스 시간표 응답 형식이 예상과 다릅니다.")
        }
        // 키 없이 부르면 나이스가 샘플 5건만 준다(list_total_count는 실제 건수). 교시가 빠진 시간표를 정상으로 주지 않는다.
        if (totalCount.intValue() > rows.size()) {
            throw NeisApiException(UNEXPECTED_RESPONSE, "나이스 시간표를 일부만 받았습니다.")
        }
        return rows.values()
            .map(::toTimetable)
            .sortedWith(compareBy(NeisTimetable::date, NeisTimetable::period))
    }

    // 날짜·교시·과목 중 하나라도 없거나 읽을 수 없는 행이 있으면 응답 전체를 깨진 것으로 본다.
    // 빈 과목을 정상 시간표로 주고 10분 동안 캐시하지 않으려는 것이다. 실패는 GetTimetableUseCase가 캐시하지 않는다.
    private fun toTimetable(row: JsonNode): NeisTimetable {
        val date = row.requiredString("ALL_TI_YMD")?.let(::parseDate)
        val period = row.requiredString("PERIO")?.toIntOrNull()
        val subject = row.requiredString("ITRT_CNTNT")
        if (date == null || period == null || subject == null) {
            throw NeisApiException(UNEXPECTED_RESPONSE, "나이스 시간표 행에 날짜, 교시, 과목이 올바르지 않습니다.")
        }
        return NeisTimetable(date, period, subject)
    }

    // 문자열이고 앞뒤 공백을 지워도 비어 있지 않을 때만 지운 값을 준다.
    private fun JsonNode.requiredString(field: String): String? {
        val node = path(field)
        if (!node.isString) return null
        return node.stringValue().trim().ifEmpty { null }
    }

    private fun parseDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value, DATE_FORMAT)
        } catch (e: DateTimeParseException) {
            null
        }

    companion object {
        private const val PATH = "/hub/hisTimetable"
        private const val SERVICE = "hisTimetable"
        private const val OK = "INFO-000"
        private const val NO_DATA = "INFO-200"
        private const val UNEXPECTED_RESPONSE = "UNEXPECTED_RESPONSE"

        // 한 주(7일) × 하루 많아야 10교시 안쪽이라 한 번에 다 받는다.
        private const val PAGE_SIZE = 100
        private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
    }
}
