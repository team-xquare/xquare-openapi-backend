package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.AccessLevel
import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectField
import app.xquare.xquareopenapi.domain.apikey.ApplicationStatus

// 프로젝트 만들기·분야 추가 신청의 응답. apiKey는 이번 요청으로 키가 처음 발급됐을 때만 원문이 있고, 아니면 null이다.
data class ProjectApplyResponse(
    val projectId: Long,
    val apiKey: String?,
    // 프로젝트의 모든 분야(처음 신청한 순서)
    val fields: List<FieldStatus>,
) {
    data class FieldStatus(
        val field: ApiKeyField,
        val level: AccessLevel,
        val status: ApplicationStatus,
    )

    companion object {
        fun of(projectId: Long, apiKey: String?, fields: List<ApiProjectField>) = ProjectApplyResponse(
            projectId = projectId,
            apiKey = apiKey,
            fields = fields.map { FieldStatus(it.field, it.field.level, it.status) },
        )
    }
}
