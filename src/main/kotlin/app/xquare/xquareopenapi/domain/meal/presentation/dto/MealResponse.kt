package app.xquare.xquareopenapi.domain.meal.presentation.dto

import app.xquare.xquareopenapi.domain.meal.Meal
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(requiredProperties = ["id", "date", "breakfast", "lunch", "dinner"])
data class MealResponse(
    val id: Long,
    val date: LocalDate,
    val breakfast: List<String>,
    val lunch: List<String>,
    val dinner: List<String>,
) {
    companion object {
        fun from(meal: Meal) = MealResponse(
            id = meal.id!!,
            date = meal.date,
            breakfast = meal.breakfast,
            lunch = meal.lunch,
            dinner = meal.dinner,
        )
    }
}
