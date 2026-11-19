package app.xquare.xquareopenapi.domain.studysupervisor

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface StudySupervisorAssignmentRepository : JpaRepository<StudySupervisorAssignment, Long> {

    // 조회 응답에 교직원 이름을 넣으므로 같이 읽어 배정마다 한 번씩 더 조회하지 않게 한다.
    @Query("select a from StudySupervisorAssignment a join fetch a.teacher where a.studySupervisor.id in :studySupervisorIds")
    fun findAllWithTeacherByStudySupervisorIdIn(studySupervisorIds: Collection<Long>): List<StudySupervisorAssignment>

    // 수정이 쓴다. 바로 실행되므로 이어서 같은 층을 넣어도 unique 제약에 걸리지 않는다.
    @Modifying
    @Query("delete from StudySupervisorAssignment a where a.studySupervisor.id = :studySupervisorId")
    fun deleteAllByStudySupervisorId(studySupervisorId: Long): Int
}
