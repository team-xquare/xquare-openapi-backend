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
    // 세 끼를 모두 비워 등록하면 '급식 없음' 표시가 되어, 나이스 동기화가 그 날짜를 만들지 못하게 미리 막는다.
    @Transactional
    fun execute(request: MealRequest): Long {
        val existing = mealRepository.findByDate(request.date)
        if (existing != null) {
            if (!existing.isNoMeal) throw XquareException(ErrorCode.MEAL_ALREADY_EXISTS)
            // '급식 없음' 표시는 없는 급식으로 보고, 그 행을 새 메뉴로 바꾼다.
            existing.update(request.date, request.breakfast, request.lunch, request.dinner)
            return existing.id!!
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
