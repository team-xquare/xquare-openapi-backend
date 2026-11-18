package app.xquare.xquareopenapi.domain.schedule.usecase

import app.xquare.xquareopenapi.domain.schedule.Schedule
import app.xquare.xquareopenapi.domain.schedule.ScheduleRepository
import app.xquare.xquareopenapi.domain.schedule.ScheduleSource
import app.xquare.xquareopenapi.domain.schedule.neis.NeisSchedule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@Service
class SyncNeisSchedulesUseCase(
    private val scheduleRepository: ScheduleRepository,
) {

    // 한 달 치 나이스 학사일정으로 그 달의 나이스 일정(source=NEIS)을 맞춘다. 학교 일정(SCHOOL)은 읽지도 바꾸지도 않는다.
    // 나이스는 행 id를 주지 않아 (날짜, 제목)이 같으면 같은 일정으로 본다. 같은 일정은 id를 유지하고 내용만 바꾼다.
    // 나이스에서 사라진 일정은 지운다. 관리자가 나이스 일정을 지울 수 없어서, 남겨 두면 날짜가 바뀐 행사가 두 날짜에 계속 남는다.
    // 같은 (날짜, 제목)이 여러 행이면(나이스 중복이나 동시 동기화) 하나만 남긴다.
    @Transactional
    fun execute(month: YearMonth, neisSchedules: List<NeisSchedule>): ScheduleSyncResult {
        val existing = scheduleRepository.findAllBySourceAndDateBetween(ScheduleSource.NEIS, month.atDay(1), month.atEndOfMonth())
            .groupBy { it.date to it.title }
        // 그 달이 아닌 날짜는 버린다. 다른 달 행은 위에서 읽지 않아 같은 일정을 못 찾고 매번 새로 만들게 된다.
        val fetched = neisSchedules.filter { YearMonth.from(it.date) == month }
            .associateBy { it.date to it.title.take(Schedule.TITLE_COLUMN_LENGTH) }
        val kept = existing.filterKeys { it in fetched }.mapValues { it.value.first() }
        kept.forEach { (key, schedule) -> schedule.updateFromNeis(fetched.getValue(key).content?.take(Schedule.CONTENT_COLUMN_LENGTH)) }
        val stale = existing.values.flatten() - kept.values.toSet()
        scheduleRepository.deleteAll(stale)
        val created = fetched.filterKeys { it !in kept }.map { (key, neisSchedule) ->
            Schedule(key.first, key.second, neisSchedule.content?.take(Schedule.CONTENT_COLUMN_LENGTH), ScheduleSource.NEIS)
        }
        scheduleRepository.saveAll(created)
        return ScheduleSyncResult(created = created.size, updated = kept.size, deleted = stale.size)
    }
}

data class ScheduleSyncResult(
    val created: Int,
    val updated: Int,
    val deleted: Int,
)
