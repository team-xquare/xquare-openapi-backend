package app.xquare.xquareopenapi.domain.meal.usecase

import app.xquare.xquareopenapi.domain.meal.Meal
import app.xquare.xquareopenapi.domain.meal.MealRepository
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateMealUseCase(
    private val mealRepository: MealRepository,
) {

    // 수정한 급식은 이후 나이스 동기화가 덮어쓰지 않는다.
    @Transactional
    fun execute(mealId: Long, request: MealRequest) {
        val meal = mealRepository.findByIdOrNull(mealId) ?: throw XquareException(ErrorCode.MEAL_NOT_FOUND)
        if (request.date != meal.date && mealRepository.existsByDate(request.date)) {
            throw XquareException(ErrorCode.MEAL_ALREADY_EXISTS)
        }
        meal.update(request.date, request.breakfast, request.lunch, request.dinner)
        // 커밋할 때 반영하면 unique 제약 위반이 이 메서드 밖에서 나므로, 여기서 반영해 409로 바꾼다.
        try {
            mealRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(Meal.DATE_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.MEAL_ALREADY_EXISTS)
            throw e
        }
    }
}
