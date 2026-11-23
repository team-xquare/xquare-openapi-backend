package app.xquare.xquareopenapi.domain.apiusage.presentation

import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLog
import app.xquare.xquareopenapi.domain.apiusage.UsageHttpMethod
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.time.ZoneOffset

data class ApiUsageLogQuery(
    @field:Schema(description = "조회 시작 시각(포함). to와 함께 지정합니다.", type = "string", format = "date-time")
    val from: Instant? = null,
    @field:Schema(description = "조회 종료 시각(제외). from과 함께 지정합니다.", type = "string", format = "date-time")
    val to: Instant? = null,
    val method: UsageHttpMethod? = null,
    @field:Min(100) @field:Max(599)
    val status: Int? = null,
    @field:Min(0) @field:Max(1000)
    @field:Schema(defaultValue = "0")
    val page: Int = 0,
    @field:Min(1) @field:Max(100)
    @field:Schema(defaultValue = "20")
    val size: Int = 20,
)

@Schema(requiredProperties = ["id", "occurredAt", "method", "route", "status", "durationMs"])
data class ApiUsageLogResponse(
    val id: Long,
    val occurredAt: Instant,
    val method: UsageHttpMethod,
    @field:Schema(maxLength = 160)
    val route: String,
    val status: Int,
    @field:Min(0)
    val durationMs: Long,
) {
    companion object {
        fun from(log: ApiUsageLog) = ApiUsageLogResponse(
            requireNotNull(log.id), log.occurredAt.toInstant(ZoneOffset.UTC), UsageHttpMethod.valueOf(log.method),
            log.route, log.status, log.durationMs,
        )
    }
}
