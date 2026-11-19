package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplication
import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationType
import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apikey.withSeoulOffset
import java.time.OffsetDateTime

data class ApiKeyApplicationSummaryResponse(
    val id: Long,
    val userId: Long,
    val field: ApiKeyField,
    val type: ApiKeyApplicationType,
    val serviceName: String,
    val purpose: String,
    // 한국 시간(+09:00)
    val appliedAt: OffsetDateTime,
) {
    companion object {
        fun of(application: ApiKeyApplication, userId: Long) = ApiKeyApplicationSummaryResponse(
            id = application.id!!,
            userId = userId,
            field = application.field,
            type = application.type,
            serviceName = application.serviceName,
            purpose = application.purpose,
            appliedAt = application.appliedAt.withSeoulOffset(),
        )
    }
}
