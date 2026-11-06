package app.xquare.xquareopenapi.domain.meal.usecase

import app.xquare.xquareopenapi.domain.meal.MealRepository
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealListResponse
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealResponse
import app.xquare.xquareopenapi.domain.meal.presentation.dto.MealSearchRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Service
class GetMealsUseCase(
    private val mealRepository: MealRepository,
    private val clock: Clock,
) {

    // 날짜 순으로 준다. 급식이 없는 날짜는 빠진다.
    // 기간을 생략하면 기능 명세서대로 이번 달을 보여 주고, 하나만 주면 그 날짜가 속한 달의 1일 또는 말일로 채운다.
    @Transactional(readOnly = true)
    fun execute(request: MealSearchRequest): MealListResponse {
        val today = LocalDate.now(clock)
        val startDate = request.startDate ?: (request.finishDate ?: today).withDayOfMonth(1)
        val finishDate = request.finishDate ?: (request.startDate ?: today).with(TemporalAdjusters.lastDayOfMonth())
        if (finishDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, finishDate) >= MAX_DAYS) {
            throw XquareException(ErrorCode.INVALID_DATE_RANGE)
        }
        val meals = mealRepository.findAllByDateBetweenOrderByDateAsc(startDate, finishDate).map(MealResponse::from)
        return MealListResponse(startDate, finishDate, meals, meals.size)
    }

    companion object {
        // 시작일과 종료일을 포함해 92일(석 달)까지 조회한다. 달력의 앞뒤 주를 함께 보여 줘도 충분한 길이다.
        private const val MAX_DAYS = 92
    }
}
