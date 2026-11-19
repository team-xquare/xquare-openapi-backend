package app.xquare.xquareopenapi.domain.studysupervisor

import app.xquare.xquareopenapi.domain.teacher.Teacher
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

// 한 층의 감독 교직원 배정이다. 수정하면 그 날짜의 배정을 모두 지우고 새로 넣는다.
@Entity
@Table(
    name = "study_supervisor_assignments",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_study_supervisor_assignments_floor", columnNames = ["study_supervisor_id", "floor_number"]),
    ],
)
class StudySupervisorAssignment(
    studySupervisor: StudySupervisor,
    floor: Int,
    teacher: Teacher,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // 자습 감독 정보를 지우면 DB가 배정도 지운다(ON DELETE CASCADE).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_supervisor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val studySupervisor: StudySupervisor = studySupervisor

    @Column(name = "floor_number", nullable = false)
    val floor: Int = floor

    // 감독 교직원을 지우면 DB가 이 배정만 지운다(ON DELETE CASCADE). 자습 감독 정보는 남고 그 층은 teacher가 null로 나온다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val teacher: Teacher = teacher
}
