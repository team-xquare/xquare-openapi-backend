package app.xquare.xquareopenapi.domain.apikey

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

// 프로젝트의 분야 하나에 대한 신청·승인 상태.
// 교내 분야는 신청과 동시에 승인(APPROVED), 제한 분야는 관리자 승인 대기(PENDING)로 시작한다.
@Entity
// 제약 이름은 같은 분야를 두 번 신청했는지 구분하는 계약이라 직접 정한다.
@Table(
    name = "api_project_fields",
    uniqueConstraints = [
        UniqueConstraint(name = ApiProjectField.FIELD_UNIQUE_CONSTRAINT, columnNames = ["project_id", "field"]),
    ],
)
class ApiProjectField(
    @Column(name = "project_id", nullable = false)
    val projectId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 30)
    val field: ApiKeyField,

    appliedAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ApplicationStatus = statusOnSubmit(field)
        protected set

    @Column(name = "reject_reason", length = 200)
    var rejectReason: String? = null
        protected set

    @Column(name = "applied_at", nullable = false)
    var appliedAt: LocalDateTime = appliedAt
        protected set

    // 승인·반려 시각. 교내 분야는 자동 승인이라 신청 시각과 같다.
    @Column(name = "decided_at")
    var decidedAt: LocalDateTime? = decidedAtOnSubmit(status, appliedAt)
        protected set

    // 처리한 관리자의 userId. 자동 승인이면 null이다.
    @Column(name = "decided_by")
    var decidedBy: Long? = null
        protected set

    // 반려된 분야를 다시 신청한다. 처음 신청할 때와 같은 상태로 돌아가고 반려 사유와 처리 기록을 지운다.
    fun resubmit(now: LocalDateTime) {
        status = statusOnSubmit(field)
        appliedAt = now
        decidedAt = decidedAtOnSubmit(status, now)
        decidedBy = null
        rejectReason = null
    }

    // 관리자 승인. 대기(PENDING)인지는 부르는 쪽(DecideApiApplicationUseCase)이 확인한다.
    fun approve(adminId: Long, now: LocalDateTime) {
        status = ApplicationStatus.APPROVED
        decidedAt = now
        decidedBy = adminId
    }

    // 관리자 반려. 사유는 사용자에게 그대로 보인다.
    fun reject(adminId: Long, reason: String, now: LocalDateTime) {
        status = ApplicationStatus.REJECTED
        rejectReason = reason
        decidedAt = now
        decidedBy = adminId
    }

    companion object {
        const val FIELD_UNIQUE_CONSTRAINT = "uk_api_project_fields_project_id_field"

        private fun statusOnSubmit(field: ApiKeyField) =
            if (field.level == AccessLevel.SCHOOL) ApplicationStatus.APPROVED else ApplicationStatus.PENDING

        private fun decidedAtOnSubmit(status: ApplicationStatus, now: LocalDateTime) =
            if (status == ApplicationStatus.APPROVED) now else null
    }
}
