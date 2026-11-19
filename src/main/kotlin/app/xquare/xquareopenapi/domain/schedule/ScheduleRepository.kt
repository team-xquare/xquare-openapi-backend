package app.xquare.xquareopenapi.domain.schedule

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface ScheduleRepository : JpaRepository<Schedule, Long> {

    // 나이스 동기화가 그 달의 나이스 일정을 읽는다. 학교 일정은 읽지 않으므로 동기화가 건드릴 일이 없다.
    fun findAllBySourceAndDateBetween(source: ScheduleSource, startDate: LocalDate, finishDate: LocalDate): List<Schedule>

    // 조회 후 삭제하면 동시 삭제 때 늦은 쪽이 0행 삭제로 500이 난다. 출처 조건까지 한 번에 걸어 지우고 지운 행 수를 돌려준다.
    @Modifying
    @Query("delete from Schedule s where s.id = :id and s.source = :source")
    fun deleteByIdAndSourceReturningCount(id: Long, source: ScheduleSource): Int
}
