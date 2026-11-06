package app.xquare.xquareopenapi.domain.student

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface StudentRepository : JpaRepository<Student, Long> {
    fun existsByStudentNumber(studentNumber: Int): Boolean

    // name은 escapeLike()를 거친 값이어야 한다.
    // 학년·반은 학번에서 나온다. 2학년은 2000~2999, 3반은 학번을 1000으로 나눈 나머지가 300~399다.
    // 응답에 소속 동아리를 넣으므로 같이 읽어 학생마다 한 번씩 더 조회하지 않게 한다.
    @EntityGraph(attributePaths = ["club"])
    @Query(
        """
        select s from Student s
        where (:name is null or s.name like concat('%', :name, '%') escape '!')
          and (:grade is null or s.studentNumber between :grade * 1000 and :grade * 1000 + 999)
          and (:classNum is null or mod(s.studentNumber, 1000) between :classNum * 100 and :classNum * 100 + 99)
        """,
    )
    fun search(name: String?, grade: Int?, classNum: Int?, pageable: Pageable): Page<Student>

    // 동아리 등록·수정이 쓴다. 다른 동아리 등록·수정이 같은 학생을 동시에 넣지 못하게 행을 잠그고 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id in :ids")
    fun findAllByIdInForUpdate(ids: Collection<Long>): List<Student>

    // 동아리 구성원을 학번 순으로 읽는다.
    @Query("select s from Student s where s.club.id in :clubIds order by s.studentNumber")
    fun findAllByClubIdIn(clubIds: Collection<Long>): List<Student>

    // 조회 후 삭제하면 동시 삭제 때 늦은 쪽이 커밋하며 0행 삭제로 500이 난다. 한 번에 지우고 지운 행 수를 돌려준다.
    @Modifying
    @Query("delete from Student s where s.id = :id")
    fun deleteByIdReturningCount(id: Long): Int
}
