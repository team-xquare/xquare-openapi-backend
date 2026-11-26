package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectField
import app.xquare.xquareopenapi.domain.apikey.withSeoulOffset
import java.time.OffsetDateTime

// 내 프로젝트 상세. 목록 항목에 만든 시각을 더한다. 사용 목적은 분야마다 fields에 있다.
data class ProjectDetailResponse(
    val projectId: Long,
    val name: String,
    // 한국 시간(+09:00)
    val createdAt: OffsetDateTime,
    val keyIssued: Boolean,
    val issuedAt: OffsetDateTime?,
    val expiresAt: OffsetDateTime?,
    val fields: List<ProjectFieldResponse>,
) {
    companion object {
        fun of(project: ApiProject, fields: List<ApiProjectField>) = ProjectDetailResponse(
            projectId = project.id!!,
            name = project.name,
            createdAt = project.createdAt.withSeoulOffset(),
            keyIssued = project.keyIssued,
            issuedAt = project.issuedAt?.withSeoulOffset(),
            expiresAt = project.expiresAt?.withSeoulOffset(),
            fields = fields.map(ProjectFieldResponse::from),
        )
    }
}
