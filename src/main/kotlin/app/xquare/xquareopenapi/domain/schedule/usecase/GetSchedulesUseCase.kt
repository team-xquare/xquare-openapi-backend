package app.xquare.xquareopenapi.domain.schedule.usecase

import app.xquare.xquareopenapi.domain.schedule.ScheduleRepository
import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class GetSchedulesUseCase(private val scheduleRepository: ScheduleRepository) {
    @Transactional(readOnly = true, timeout = 5)
    fun execute(startDate: LocalDate, finishDate: LocalDate): List<ScheduleResponse> {
        if (finishDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, finishDate) >= MAX_DAYS) {
            throw XquareException(ErrorCode.INVALID_DATE_RANGE)
        }

        // 한 건만 더 읽어 초과를 감지한다. 잘린 목록을 성공으로 반환하지 않는다.
        val schedules = scheduleRepository.findAllByDateBetweenOrderByDateAscIdAsc(
            startDate, finishDate, Limit.of(MAX_RESULTS + 1),
        )
        if (schedules.size > MAX_RESULTS) throw XquareException(ErrorCode.SCHEDULE_LIST_LIMIT_EXCEEDED)
        return schedules.map(ScheduleResponse::from)
    }

    companion object {
        const val MAX_DAYS = 92
        const val MAX_RESULTS = 500
    }
}