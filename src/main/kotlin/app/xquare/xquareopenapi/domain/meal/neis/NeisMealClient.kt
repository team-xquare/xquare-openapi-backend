package app.xquare.xquareopenapi.domain.meal.neis

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// 나이스 급식식단정보(mealServiceDietInfo)를 월 단위로 가져온다.
@Component
class NeisMealClient(
    private val neisRestClient: RestClient,
    private val neisProperties: NeisProperties,
) {

    // 그 달의 급식을 날짜 순으로 준다. 데이터가 없으면(INFO-200) 빈 목록이다.
    fun fetchMonth(month: YearMonth): List<NeisMeal> {
        val response = neisRestClient.get()
            .uri {
                it.path(PATH)
                    .queryParam("KEY", neisProperties.apiKey)
                    .queryParam("Type", "json")
                    .queryParam("pIndex", 1)
                    .queryParam("pSize", PAGE_SIZE)
                    .queryParam("ATPT_OFCDC_SC_CODE", neisProperties.officeCode)
                    .queryParam("SD_SCHUL_CODE", neisProperties.schoolCode)
                    .queryParam("MLSV_FROM_YMD", month.atDay(1).format(DATE_FORMAT))
                    .queryParam("MLSV_TO_YMD", month.atEndOfMonth().format(DATE_FORMAT))
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
        // 정상이면 {"mealServiceDietInfo":[{"head":[...]},{"row":[...]}]}로 온다.
        val rows = response.path(SERVICE).path(1).path("row").values()
        return rows.groupBy { LocalDate.parse(it.path("MLSV_YMD").asString(), DATE_FORMAT) }
            .map { (date, dayRows) ->
                NeisMeal(date, dishes(dayRows, BREAKFAST), dishes(dayRows, LUNCH), dishes(dayRows, DINNER))
            }
            // 조식·중식·석식이 아닌 끼니만 있는 날은 급식이 없는 날로 본다.
            .filter { it.breakfast.isNotEmpty() || it.lunch.isNotEmpty() || it.dinner.isNotEmpty() }
            .sortedBy(NeisMeal::date)
    }

    private fun dishes(dayRows: List<JsonNode>, mealCode: String): List<String> =
        dayRows.filter { it.path("MMEAL_SC_CODE").asString() == mealCode }
            .flatMap { parseDishes(it.path("DDISH_NM").asString()) }

    companion object {
        private const val PATH = "/hub/mealServiceDietInfo"
        private const val SERVICE = "mealServiceDietInfo"
        private const val NO_DATA = "INFO-200"

        // 한 달은 많아야 31일 × 3끼 = 93건이라 한 번에 다 받는다.
        private const val PAGE_SIZE = 100
        private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE

        // MMEAL_SC_CODE: 1 조식, 2 중식, 3 석식
        private const val BREAKFAST = "1"
        private const val LUNCH = "2"
        private const val DINNER = "3"
    }
}
