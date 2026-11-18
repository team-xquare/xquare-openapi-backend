package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplication
import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import java.time.LocalDateTime

// 키가 신청한 분야 하나와 그 신청 시각
data class AppliedFieldResponse(
    val field: ApiKeyField,
    val appliedAt: LocalDateTime,
) {
    companion object {
        fun from(application: ApiKeyApplication) = AppliedFieldResponse(application.field, application.appliedAt)
    }
}
