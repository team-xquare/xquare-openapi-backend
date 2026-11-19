package app.xquare.xquareopenapi.domain.schedule.usecase

import app.xquare.xquareopenapi.domain.schedule.ScheduleRepository
import app.xquare.xquareopenapi.domain.schedule.ScheduleSource
import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateScheduleUseCase(
    private val scheduleRepository: ScheduleRepository,
) {

    // 나이스 일정은 다음 동기화가 되돌리므로 수정할 수 없다. 학교 일정은 동기화가 건드리지 않아 잠그지 않는다.
    @Transactional
    fun execute(scheduleId: Long, request: ScheduleRequest) {
        val schedule = scheduleRepository.findByIdOrNull(scheduleId) ?: throw XquareException(ErrorCode.SCHEDULE_NOT_FOUND)
        if (schedule.source == ScheduleSource.NEIS) throw XquareException(ErrorCode.NEIS_SCHEDULE_NOT_EDITABLE)
        schedule.update(request.date, request.title, request.content)
    }
}
