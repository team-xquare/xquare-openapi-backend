package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.AccessLevel
import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectField
import app.xquare.xquareopenapi.domain.apikey.ApplicationStatus
import app.xquare.xquareopenapi.domain.apikey.withSeoulOffset
import java.time.OffsetDateTime

// 내 프로젝트 목록의 항목. 키 원문은 해시만 저장하므로 담지 않는다.
data class ProjectSummaryResponse(
    val projectId: Long,
    val name: String,
    val keyIssued: Boolean,
    // 지금 키 값을 발급한 시각. 발급 전이면 null. 한국 시간(+09:00)
    val issuedAt: OffsetDateTime?,
    // 만료 시각(발급·재발급·갱신 + 60일). 발급 전이면 null. 한국 시간(+09:00)
    val expiresAt: OffsetDateTime?,
    // 처음 신청한 순서
    val fields: List<ProjectFieldResponse>,
) {
    companion object {
        fun of(project: ApiProject, fields: List<ApiProjectField>) = ProjectSummaryResponse(
            projectId = project.id!!,
            name = project.name,
            keyIssued = project.keyIssued,
            issuedAt = project.issuedAt?.withSeoulOffset(),
            expiresAt = project.expiresAt?.withSeoulOffset(),
            fields = fields.map(ProjectFieldResponse::from),
        )
    }
}

// 프로젝트의 분야 하나와 사용 목적, 신청 상태. 반려됐으면 사유가 있다.
data class ProjectFieldResponse(
    val field: ApiKeyField,
    val level: AccessLevel,
    val status: ApplicationStatus,
    val purpose: String,
    val rejectReason: String?,
) {
    companion object {
        fun from(field: ApiProjectField) =
            ProjectFieldResponse(field.field, field.field.level, field.status, field.purpose, field.rejectReason)
    }
}
