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
    // 그 달 밖의 날짜가 하나라도 있으면 요청과 다른 응답으로 보고 아무것도 바꾸지 않는다(IllegalArgumentException).
    // 걸러서 쓰면 다른 달 행만 온 응답이 빈 목록이 되어 그 달 나이스 일정을 모두 지운다. 받은 목록 자체가 빈 것(데이터 없음, INFO-200)은 정상이라 모두 지운다.
    @Transactional
    fun execute(month: YearMonth, neisSchedules: List<NeisSchedule>): ScheduleSyncResult {
        neisSchedules.firstOrNull { YearMonth.from(it.date) != month }?.let {
            throw IllegalArgumentException("$month 동기화에 그 달 밖의 나이스 학사일정(${it.date})이 왔습니다.")
        }
        val existing = scheduleRepository.findAllBySourceAndDateBetween(ScheduleSource.NEIS, month.atDay(1), month.atEndOfMonth())
            .groupBy { it.date to it.title }
        val fetched = neisSchedules.associateBy { it.date to it.title.take(Schedule.TITLE_COLUMN_LENGTH) }
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
