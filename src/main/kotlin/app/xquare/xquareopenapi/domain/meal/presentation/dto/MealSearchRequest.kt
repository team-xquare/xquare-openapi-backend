package app.xquare.xquareopenapi.domain.meal.presentation.dto

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

// 둘 다 생략하면 이번 달, 하나만 주면 그 날짜가 속한 달의 1일 또는 말일로 채운다(GetMealsUseCase).
data class MealSearchRequest(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val startDate: LocalDate? = null,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val finishDate: LocalDate? = null,
)
