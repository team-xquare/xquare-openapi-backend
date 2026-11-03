package app.xquare.xquareopenapi.domain.student

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface StudentRepository : JpaRepository<Student, Long> {
    fun existsByStudentNumber(studentNumber: Int): Boolean

    // 학년·반은 학번에서 나온다. 2학년은 2000~2999, 3반은 학번을 1000으로 나눈 나머지가 300~399다.
    @Query(
        """
        select s from Student s
        where (:name is null or s.name like concat('%', :name, '%'))
          and (:grade is null or s.studentNumber between :grade * 1000 and :grade * 1000 + 999)
          and (:classNum is null or mod(s.studentNumber, 1000) between :classNum * 100 and :classNum * 100 + 99)
        """,
    )
    fun search(name: String?, grade: Int?, classNum: Int?, pageable: Pageable): Page<Student>
}
