package app.xquare.xquareopenapi.domain.student

import app.xquare.xquareopenapi.domain.club.Club
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
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
import java.time.LocalDate

@Entity
// 제약 이름은 동시 등록·수정으로 학번이 겹쳤는지 구분하는 데 쓰므로 직접 정한다.
@Table(
    name = "students",
    uniqueConstraints = [UniqueConstraint(name = Student.STUDENT_NUMBER_UNIQUE_CONSTRAINT, columnNames = ["student_number"])],
)
class Student(
    studentNumber: Int,
    name: String,
    birthDate: LocalDate,
    email: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // 앞 1자리가 학년, 다음 1자리가 반, 마지막 2자리가 번호다.
    @Column(name = "student_number", nullable = false)
    var studentNumber: Int = validStudentNumber(studentNumber)
        protected set

    @Column(nullable = false, length = 10)
    var name: String = name
        protected set

    @Column(nullable = false)
    var birthDate: LocalDate = birthDate
        protected set

    @Column(nullable = false)
    var email: String = email
        protected set

    // 소속 동아리. 한 학생은 한 동아리에만 속하고, 동아리 등록·수정의 member로만 바뀐다(학생 수정으로는 안 바뀐다).
    // 동아리를 지우면 DB가 null로 바꾼다(ON DELETE SET NULL).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var club: Club? = null
        protected set

    fun joinClub(club: Club) {
        this.club = club
    }

    fun leaveClub() {
        club = null
    }

    // clubId는 지금 등록·수정하는 동아리 id다. 등록이면 null이라 어느 동아리에든 속해 있으면 true다.
    fun belongsToOtherClub(clubId: Long?): Boolean {
        val current = club
        return current != null && current.id != clubId
    }

    fun update(studentNumber: Int, name: String, birthDate: LocalDate, email: String) {
        this.studentNumber = validStudentNumber(studentNumber)
        this.name = name
        this.birthDate = birthDate
        this.email = email
    }

    companion object {
        const val STUDENT_NUMBER_UNIQUE_CONSTRAINT = "uk_students_student_number"

        private fun validStudentNumber(studentNumber: Int): Int {
            if (studentNumber !in 1000..3999) throw XquareException(ErrorCode.INVALID_STUDENT_NUMBER)
            return studentNumber
        }
    }
}
