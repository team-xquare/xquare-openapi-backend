package app.xquare.xquareopenapi.domain.club

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ClubRepository : JpaRepository<Club, Long>, JpaSpecificationExecutor<Club> {

    // 목록 조회가 쓴다. 팀장·담당 교직원을 같이 읽어 동아리마다 한 번씩 더 조회하지 않게 한다.
    @EntityGraph(attributePaths = ["manager", "teacher"])
    override fun findAll(spec: Specification<Club>, pageable: Pageable): Page<Club>

    @EntityGraph(attributePaths = ["manager", "teacher"])
    @Query("select c from Club c where c.id = :id")
    fun findWithManagerAndTeacherById(id: Long): Club?

    // 같은 동아리를 동시에 수정하면 구성원 교체가 섞이므로 동아리 행을 잠그고 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Club c where c.id = :id")
    fun findByIdForUpdate(id: Long): Club?

    // exceptId는 수정 중인 동아리 id다. 자기 자신이 쓰던 교실·교직원은 겹친 것으로 보지 않는다. 등록이면 null.
    @Query("select count(c) > 0 from Club c where c.room = :room and (:exceptId is null or c.id <> :exceptId)")
    fun existsByRoomExcept(room: ClubRoom, exceptId: Long?): Boolean

    @Query("select count(c) > 0 from Club c where c.teacher.id = :teacherId and (:exceptId is null or c.id <> :exceptId)")
    fun existsByTeacherExcept(teacherId: Long, exceptId: Long?): Boolean

    @Query("select c from Club c where c.teacher.id in :teacherIds")
    fun findAllByTeacherIdIn(teacherIds: Collection<Long>): List<Club>

    // 조회 후 삭제하면 동시 삭제 때 늦은 쪽이 커밋하며 0행 삭제로 500이 난다. 한 번에 지우고 지운 행 수를 돌려준다.
    // 구성원 학생의 소속은 DB가 null로 바꾼다(students.club_id ON DELETE SET NULL).
    @Modifying
    @Query("delete from Club c where c.id = :id")
    fun deleteByIdReturningCount(id: Long): Int
}
