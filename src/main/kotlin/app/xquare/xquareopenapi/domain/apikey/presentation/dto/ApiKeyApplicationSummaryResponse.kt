package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplication
import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationType
import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import java.time.LocalDateTime

data class ApiKeyApplicationSummaryResponse(
    val id: Long,
    val userId: Long,
    val field: ApiKeyField,
    val type: ApiKeyApplicationType,
    val serviceName: String,
    val purpose: String,
    val appliedAt: LocalDateTime,
) {
    companion object {
        fun of(application: ApiKeyApplication, userId: Long) = ApiKeyApplicationSummaryResponse(
            id = application.id!!,
            userId = userId,
            field = application.field,
            type = application.type,
            serviceName = application.serviceName,
            purpose = application.purpose,
            appliedAt = application.appliedAt,
        )
    }
}
