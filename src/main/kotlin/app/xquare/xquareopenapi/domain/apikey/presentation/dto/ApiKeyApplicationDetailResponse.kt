package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplication
import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationType
import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.user.User
import java.time.LocalDateTime

data class ApiKeyApplicationDetailResponse(
    val id: Long,
    val field: ApiKeyField,
    val type: ApiKeyApplicationType,
    val serviceName: String,
    val purpose: String,
    val appliedAt: LocalDateTime,
    val userId: Long,
    // api_keys.user_id에 외래 키가 없어서 사용자 행이 지워졌으면 null이다.
    val applicant: Applicant?,
    // 이 신청이 속한 키가 신청한 분야 전체(신청 순서대로)
    val fields: List<AppliedFieldResponse>,
) {
    data class Applicant(
        val username: String,
        val name: String,
        val email: String,
        val studentNumber: Int,
    )

    companion object {
        fun of(
            application: ApiKeyApplication,
            userId: Long,
            user: User?,
            applications: List<ApiKeyApplication>,
        ) = ApiKeyApplicationDetailResponse(
            id = application.id!!,
            field = application.field,
            type = application.type,
            serviceName = application.serviceName,
            purpose = application.purpose,
            appliedAt = application.appliedAt,
            userId = userId,
            applicant = if (user != null) Applicant(user.username, user.name, user.email, user.studentNumber) else null,
            fields = applications.map(AppliedFieldResponse::from),
        )
    }
}
