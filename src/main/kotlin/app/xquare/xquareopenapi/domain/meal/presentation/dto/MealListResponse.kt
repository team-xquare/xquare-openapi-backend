package app.xquare.xquareopenapi.domain.meal.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

// startDate, finishDate는 생략한 값을 채운 실제 조회 기간이다.
@Schema(requiredProperties = ["startDate", "finishDate", "meals", "totalCount"])
data class MealListResponse(
    val startDate: LocalDate,
    val finishDate: LocalDate,
    val meals: List<MealResponse>,
    val totalCount: Int,
)
