package app.xquare.xquareopenapi.domain.meal.neis

import java.time.LocalDate

// 나이스 급식식단정보를 날짜 하나로 묶은 것. 메뉴는 parseDishes()로 정리한 값이다.
data class NeisMeal(
    val date: LocalDate,
    val breakfast: List<String>,
    val lunch: List<String>,
    val dinner: List<String>,
)
