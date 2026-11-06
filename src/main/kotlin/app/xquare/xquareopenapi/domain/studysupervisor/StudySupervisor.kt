package app.xquare.xquareopenapi.domain.studysupervisor

import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDate

// 날짜 하나에 자습 감독 정보 하나다. 층별 감독 교직원은 StudySupervisorAssignment에 따로 둔다.
@Entity
// 제약 이름은 동시 등록·수정으로 날짜가 겹쳤는지 구분하는 데 쓰므로 직접 정한다.
@Table(
    name = "study_supervisors",
    uniqueConstraints = [UniqueConstraint(name = StudySupervisor.DATE_UNIQUE_CONSTRAINT, columnNames = ["supervision_date"])],
)
class StudySupervisor(
    date: LocalDate,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "supervision_date", nullable = false)
    var date: LocalDate = date
        protected set

    fun moveTo(date: LocalDate) {
        this.date = date
    }

    companion object {
        const val DATE_UNIQUE_CONSTRAINT = "uk_study_supervisors_supervision_date"

        // 감독을 맡는 층. 명세 예시(2, 3, 4층)대로 뒀다. 조회 응답은 정보가 있는 날짜마다 이 층을 모두 주고, 배정이 없는 층은 teacher가 null이다.
        val FLOORS = 2..4

        // 중복 검사를 통과한 뒤 다른 요청이 먼저 같은 날짜를 저장하면 unique 제약에 걸린다. 이때도 검사와 같은 409로 바꾼다.
        fun conflictOf(e: DataIntegrityViolationException): Exception {
            if (e.violates(DATE_UNIQUE_CONSTRAINT)) return XquareException(ErrorCode.SUPERVISION_ALREADY_EXISTS)
            return e
        }
    }
}
