package app.xquare.xquareopenapi.domain.meal.usecase

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

    @Transactional(readOnly = true)
    fun execute(mealId: Long): MealResponse {
        val meal = mealRepository.findByIdOrNull(mealId) ?: throw XquareException(ErrorCode.MEAL_NOT_FOUND)
        return MealResponse.from(meal)
    }
}
