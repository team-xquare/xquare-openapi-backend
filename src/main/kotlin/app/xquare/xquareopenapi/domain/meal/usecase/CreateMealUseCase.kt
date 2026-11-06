package app.xquare.xquareopenapi.domain.meal.usecase

import app.xquare.xquareopenapi.domain.meal.Meal
import app.xquare.xquareopenapi.domain.meal.MealRepository
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateMealUseCase(
    private val mealRepository: MealRepository,
) {

    // 등록한 급식의 id를 돌려준다. 관리자가 등록한 급식은 나이스 동기화가 덮어쓰지 않는다.
    @Transactional
    fun execute(request: MealRequest): Long {
        if (mealRepository.existsByDate(request.date)) {
            throw XquareException(ErrorCode.MEAL_ALREADY_EXISTS)
        }
        val meal = Meal(request.date, request.breakfast, request.lunch, request.dinner, editedByAdmin = true)
        // 위의 중복 검사와 저장 사이에 같은 날짜가 먼저 저장되면(동시 등록, 나이스 동기화) unique 제약에 걸린다. 이때도 409로 응답한다.
        try {
            mealRepository.saveAndFlush(meal)
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(Meal.DATE_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.MEAL_ALREADY_EXISTS)
            throw e
        }
        return meal.id!!
    }
}
