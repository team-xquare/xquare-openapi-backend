package app.xquare.xquareopenapi.domain.schedule.usecase

import app.xquare.xquareopenapi.domain.schedule.Schedule
import app.xquare.xquareopenapi.domain.schedule.ScheduleRepository
import app.xquare.xquareopenapi.domain.schedule.ScheduleSource
import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateScheduleUseCase(
    private val scheduleRepository: ScheduleRepository,
) {

    // 관리자가 등록한 일정은 학교 일정(SCHOOL)이다. 같은 날짜·제목의 나이스 일정이 있어도 막지 않는다(명세에 에러 코드가 없다).
    @Transactional
    fun execute(request: ScheduleRequest): Long {
        val schedule = scheduleRepository.save(Schedule(request.date, request.title, request.content, ScheduleSource.SCHOOL))
        return schedule.id!!
    }
}
