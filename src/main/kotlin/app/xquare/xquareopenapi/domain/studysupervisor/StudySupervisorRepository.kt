package app.xquare.xquareopenapi.domain.studysupervisor

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface StudySupervisorRepository : JpaRepository<StudySupervisor, Long> {
    fun existsByDate(date: LocalDate): Boolean

    // 수정이 쓴다. 자기 자신의 날짜는 겹친 것으로 보지 않는다.
    fun existsByDateAndIdNot(date: LocalDate, id: Long): Boolean

    fun findAllByDateBetween(startDate: LocalDate, finishDate: LocalDate): List<StudySupervisor>

    // 같은 정보를 동시에 수정하면 배정 교체가 섞이므로 행을 잠그고 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudySupervisor s where s.id = :id")
    fun findByIdForUpdate(id: Long): StudySupervisor?

    // 조회 후 삭제하면 동시 삭제 때 늦은 쪽이 커밋하며 0행 삭제로 500이 난다. 한 번에 지우고 지운 행 수를 돌려준다.
    // 층별 배정은 DB가 지운다(study_supervisor_assignments.study_supervisor_id ON DELETE CASCADE).
    @Modifying
    @Query("delete from StudySupervisor s where s.id = :id")
    fun deleteByIdReturningCount(id: Long): Int
}
