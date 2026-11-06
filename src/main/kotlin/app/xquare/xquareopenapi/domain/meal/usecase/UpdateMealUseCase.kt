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

    // 수정한 급식은 이후 나이스 동기화가 덮어쓰지 않는다. 세 끼를 모두 비우면 '급식 없음' 표시가 된다.
    // '급식 없음' 표시는 없는 급식으로 본다. 그 id는 수정할 수 없고(404), 그 날짜로는 옮길 수 있다.
    @Transactional
    fun execute(mealId: Long, request: MealRequest) {
        val meal = mealRepository.findByIdOrNull(mealId)?.takeUnless(Meal::isNoMeal) ?: throw XquareException(ErrorCode.MEAL_NOT_FOUND)
        val originalDate = meal.date
        val moved = request.date != originalDate
        // 커밋할 때 반영하면 unique 제약 위반이 이 메서드 밖에서 나므로, 단계마다 반영해 409로 바꾼다.
        try {
            if (moved) {
                val target = mealRepository.findByDate(request.date)
                if (target != null) {
                    if (!target.isNoMeal) throw XquareException(ErrorCode.MEAL_ALREADY_EXISTS)
                    // Hibernate는 flush 때 INSERT·UPDATE를 DELETE보다 먼저 보내므로, 표시를 먼저 지워 반영해야 unique 제약에 걸리지 않는다.
                    mealRepository.delete(target)
                    mealRepository.flush()
                }
            }
            meal.update(request.date, request.breakfast, request.lunch, request.dinner)
            mealRepository.flush()
            if (moved) {
                // 원래 날짜를 비워 두면 다음 동기화가 같은 나이스 급식을 그 날짜에 다시 만든다. '급식 없음' 표시를 남겨 막는다.
                mealRepository.saveAndFlush(Meal.noMeal(originalDate))
            }
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(Meal.DATE_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.MEAL_ALREADY_EXISTS)
            throw e
        }
    }
}
