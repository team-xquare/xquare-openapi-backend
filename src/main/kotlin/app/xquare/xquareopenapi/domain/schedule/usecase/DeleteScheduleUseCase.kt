package app.xquare.xquareopenapi.domain.schedule.usecase

import app.xquare.xquareopenapi.domain.schedule.ScheduleRepository
import app.xquare.xquareopenapi.domain.schedule.ScheduleSource
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteScheduleUseCase(
    private val scheduleRepository: ScheduleRepository,
) {

    // 학교 일정만 지운다. 지운 게 없으면 남아 있는 행이 나이스 일정인지 보고 409와 404를 가른다.
    @Transactional
    fun execute(scheduleId: Long) {
        if (scheduleRepository.deleteByIdAndSourceReturningCount(scheduleId, ScheduleSource.SCHOOL) > 0) return
        if (scheduleRepository.existsById(scheduleId)) throw XquareException(ErrorCode.NEIS_SCHEDULE_NOT_EDITABLE)
        throw XquareException(ErrorCode.SCHEDULE_NOT_FOUND)
    }
}
