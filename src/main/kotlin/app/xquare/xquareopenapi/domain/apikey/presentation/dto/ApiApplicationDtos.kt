package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.AccessLevel
import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectField
import app.xquare.xquareopenapi.domain.apikey.ApplicationStatus
import app.xquare.xquareopenapi.domain.apikey.ApplicationType
import app.xquare.xquareopenapi.domain.apikey.withSeoulOffset
import app.xquare.xquareopenapi.domain.user.User
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class ApiApplicationSearchRequest(
    // 없으면 모든 상태
    @field:Parameter(description = "신청 상태 필터 (생략 시 전체)")
    val status: ApplicationStatus? = null,

    // page × size(최대 100)가 int를 넘으면 Spring Data가 offset을 만들지 못하고 500을 낸다.
    @field:Parameter(description = "페이지 번호 (0 이상)", example = "0")
    @field:Min(0)
    @field:Max(Int.MAX_VALUE / 100L)
    val page: Int = 0,

    @field:Parameter(description = "페이지 크기 (1~100)", example = "20")
    @field:Min(1)
    @field:Max(100)
    val size: Int = 20,
)

data class ApiApplicationRejectRequest(
    // 사용자에게 그대로 보여 주는 반려 사유
    @field:NotBlank
    @field:Size(max = 200)
    val reason: String,
)

// 관리자 신청 목록의 항목. 시각은 한국 시간(+09:00)
data class ApiApplicationSummaryResponse(
    val applicationId: Long,
    val projectId: Long,
    val projectName: String,
    val userId: Long,
    val field: ApiKeyField,
    val level: AccessLevel,
    val status: ApplicationStatus,
    val appliedAt: OffsetDateTime,
    val decidedAt: OffsetDateTime?,
) {
    companion object {
        fun of(field: ApiProjectField, project: ApiProject) = ApiApplicationSummaryResponse(
            applicationId = field.id!!,
            projectId = project.id!!,
            projectName = project.name,
            userId = project.userId,
            field = field.field,
            level = field.field.level,
            status = field.status,
            appliedAt = field.appliedAt.withSeoulOffset(),
            decidedAt = field.decidedAt?.withSeoulOffset(),
        )
    }
}

// 관리자 신청 상세. 신청 내용(구분·사용 목적)과 처리 기록, 프로젝트(분야 전체), 신청자를 함께 보여 준다. 시각은 한국 시간(+09:00)
data class ApiApplicationDetailResponse(
    val applicationId: Long,
    val field: ApiKeyField,
    val level: AccessLevel,
    val status: ApplicationStatus,
    val applicationType: ApplicationType,
    // 이 분야를 쓰려는 목적
    val purpose: String,
    val rejectReason: String?,
    val appliedAt: OffsetDateTime,
    val decidedAt: OffsetDateTime?,
    // 처리한 관리자의 userId. 대기 중이거나 자동 승인이면 null
    val decidedBy: Long?,
    val project: Project,
    val userId: Long,
    // api_projects.user_id에 외래 키가 없어서 사용자 행이 지워졌으면 null이다.
    val applicant: Applicant?,
) {
    data class Project(
        val projectId: Long,
        val name: String,
        val keyIssued: Boolean,
        val createdAt: OffsetDateTime,
        // 이 프로젝트의 분야 전체(처음 신청한 순서)
        val fields: List<ProjectFieldResponse>,
    )

    data class Applicant(
        val username: String,
        val name: String,
        val email: String,
        val studentNumber: Int,
    )

    companion object {
        fun of(field: ApiProjectField, project: ApiProject, projectFields: List<ApiProjectField>, user: User?) =
            ApiApplicationDetailResponse(
                applicationId = field.id!!,
                field = field.field,
                level = field.field.level,
                status = field.status,
                applicationType = field.applicationType,
                purpose = field.purpose,
                rejectReason = field.rejectReason,
                appliedAt = field.appliedAt.withSeoulOffset(),
                decidedAt = field.decidedAt?.withSeoulOffset(),
                decidedBy = field.decidedBy,
                project = Project(
                    projectId = project.id!!,
                    name = project.name,
                    keyIssued = project.keyIssued,
                    createdAt = project.createdAt.withSeoulOffset(),
                    fields = projectFields.map(ProjectFieldResponse::from),
                ),
                userId = project.userId,
                applicant = user?.let { Applicant(it.username, it.name, it.email, it.studentNumber) },
            )
    }
}
