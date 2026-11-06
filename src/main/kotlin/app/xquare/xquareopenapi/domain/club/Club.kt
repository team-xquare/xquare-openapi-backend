package app.xquare.xquareopenapi.domain.club

import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.teacher.Teacher
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
import org.springframework.dao.DataIntegrityViolationException

@Entity
// 제약 이름은 동시 등록·수정으로 교실이나 담당 교직원이 겹쳤는지 구분하는 데 쓰므로 직접 정한다.
@Table(
    name = "clubs",
    uniqueConstraints = [
        UniqueConstraint(name = Club.ROOM_UNIQUE_CONSTRAINT, columnNames = ["room"]),
        UniqueConstraint(name = Club.TEACHER_UNIQUE_CONSTRAINT, columnNames = ["teacher_id"]),
    ],
)
class Club(
    name: String,
    logo: String,
    room: ClubRoom,
    manager: Student,
    teacher: Teacher?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, length = 20)
    var name: String = name
        protected set

    @Column(nullable = false)
    var logo: String = logo
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var room: ClubRoom = room
        protected set

    // 팀장 학생을 지우면 DB가 null로 바꾼다(ON DELETE SET NULL). 관리자가 수정으로 새 팀장을 정할 때까지 비어 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var manager: Student? = manager
        protected set

    // 담당 교직원을 지우면 DB가 null로 바꾼다(ON DELETE SET NULL).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var teacher: Teacher? = teacher
        protected set

    fun update(name: String, logo: String, room: ClubRoom, manager: Student, teacher: Teacher?) {
        this.name = name
        this.logo = logo
        this.room = room
        this.manager = manager
        this.teacher = teacher
    }

    companion object {
        const val ROOM_UNIQUE_CONSTRAINT = "uk_clubs_room"
        const val TEACHER_UNIQUE_CONSTRAINT = "uk_clubs_teacher"

        // 중복 검사를 통과한 뒤 다른 요청이 먼저 같은 교실·교직원을 차지하면 unique 제약에 걸린다. 이때도 검사와 같은 에러로 바꾼다.
        fun conflictOf(e: DataIntegrityViolationException): Exception {
            if (e.violates(ROOM_UNIQUE_CONSTRAINT)) return XquareException(ErrorCode.ROOM_ALREADY_IN_USE)
            if (e.violates(TEACHER_UNIQUE_CONSTRAINT)) return XquareException(ErrorCode.TEACHER_ALREADY_IN_CLUB)
            return e
        }
    }
}
