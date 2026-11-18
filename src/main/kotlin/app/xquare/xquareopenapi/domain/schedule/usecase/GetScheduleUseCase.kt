package app.xquare.xquareopenapi.domain.schedule.usecase

import app.xquare.xquareopenapi.domain.schedule.ScheduleRepository
import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetScheduleUseCase(
    private val scheduleRepository: ScheduleRepository,
) {

    @Transactional(readOnly = true)
    fun execute(scheduleId: Long): ScheduleResponse {
        val schedule = scheduleRepository.findByIdOrNull(scheduleId) ?: throw XquareException(ErrorCode.SCHEDULE_NOT_FOUND)
        return ScheduleResponse.from(schedule)
    }
}
