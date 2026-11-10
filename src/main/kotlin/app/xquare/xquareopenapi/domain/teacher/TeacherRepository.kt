package app.xquare.xquareopenapi.domain.teacher

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface TeacherRepository : JpaRepository<Teacher, Long> {

    // 명세가 교무실만 부분 일치라고 정해서 이름은 정확히 일치하는 것만 찾는다. office는 escapeLike()를 거친 값이어야 한다.
    @Query(
        """
        select t from Teacher t
        where (:name is null or t.name = :name)
          and (:grade is null or t.grade = :grade)
          and (:classNum is null or t.classNum = :classNum)
          and (:subject is null or t.subject = :subject)
          and (:office is null or t.office like concat('%', :office, '%') escape '!')
        """,
    )
    fun search(
        name: String?,
        grade: Int?,
        classNum: Int?,
        subject: TeacherSubject?,
        office: String?,
        pageable: Pageable,
    ): Page<Teacher>

    // 동아리 등록·수정이 담당 교직원을 읽을 때 쓴다. 잠그지 않으면 읽은 뒤 교직원 삭제가 먼저 커밋돼
    // 동아리 저장이 외래 키(fk_clubs_teacher)에 걸린다(#26 리뷰). 잠그면 교직원 삭제가 동아리 커밋까지 기다린다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Teacher t where t.id = :id")
    fun findByIdForUpdate(id: Long): Teacher?

    // 조회 후 삭제하면 동시 삭제 때 늦은 쪽이 커밋하며 0행 삭제로 500이 난다. 한 번에 지우고 지운 행 수를 돌려준다.
    @Modifying
    @Query("delete from Teacher t where t.id = :id")
    fun deleteByIdReturningCount(id: Long): Int
}
