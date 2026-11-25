package app.xquare.xquareopenapi.domain.meal.presentation.dto

import io.swagger.v3.oas.annotations.Parameter
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

// 둘 다 생략하면 이번 달, 하나만 주면 그 날짜가 속한 달의 1일 또는 말일로 채운다(GetMealsUseCase).
data class MealSearchRequest(
    @field:Parameter(description = "조회 시작 날짜(yyyy-MM-dd). 생략하면 finishDate가 속한 달(없으면 이번 달)의 1일", example = "2026-09-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val startDate: LocalDate? = null,

    @field:Parameter(description = "조회 종료 날짜(yyyy-MM-dd). 생략하면 startDate가 속한 달(없으면 이번 달)의 말일", example = "2026-09-30")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val finishDate: LocalDate? = null,
)
