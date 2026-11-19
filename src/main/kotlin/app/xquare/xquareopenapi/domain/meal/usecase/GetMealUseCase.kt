package app.xquare.xquareopenapi.domain.meal.usecase

import app.xquare.xquareopenapi.domain.meal.Meal
import app.xquare.xquareopenapi.domain.meal.MealRepository
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMealUseCase(
    private val mealRepository: MealRepository,
) {

    // '급식 없음' 표시는 없는 급식으로 본다.
    @Transactional(readOnly = true)
    fun execute(mealId: Long): MealResponse {
        val meal = mealRepository.findByIdOrNull(mealId)?.takeUnless(Meal::isNoMeal) ?: throw XquareException(ErrorCode.MEAL_NOT_FOUND)
        return MealResponse.from(meal)
    }
}
