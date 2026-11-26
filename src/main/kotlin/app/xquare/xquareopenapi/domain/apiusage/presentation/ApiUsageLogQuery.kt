package app.xquare.xquareopenapi.domain.apiusage.presentation

import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLog
import app.xquare.xquareopenapi.domain.apiusage.UsageAccessLevel
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
    @field:Schema(
        description = "저장된 API 경로 템플릿의 부분 일치 검색. UNMAPPED는 제외하며, 빈 문자열 또는 공백만 입력할 수 없습니다. %, _, !는 글자 그대로 비교합니다.",
        minLength = 1,
        maxLength = API_USAGE_ROUTE_MAX_LENGTH,
        example = "/notices",
    )
    val api: String? = null,
    @field:Schema(
        description = "저장된 MVC 템플릿으로 정하는 기존 분야. 다른 필터와 AND로 적용합니다. 공개 급식과 UNMAPPED 등 미분류는 분야를 지정하면 제외합니다. 빈 값 또는 목록에 없는 값은 400입니다.",
        implementation = ApiKeyField::class,
    )
    val field: String? = null,
    @field:Schema(
        description = "저장된 접근 단계. PUBLIC(공개 급식·시간표 조회), SCHOOL·RESTRICTED(해당 단계 분야의 키 조회). 단계가 없는 기록은 지정하면 제외합니다. 빈 값 또는 목록에 없는 값은 400입니다.",
        implementation = UsageAccessLevel::class,
    )
    val level: String? = null,
    @field:Schema(description = "API 키 호출의 프로젝트 id")
    val projectId: Long? = null,
    @field:Schema(description = "호출한 사용자 id. API 키 호출이면 프로젝트 신청자, JWT 호출이면 로그인한 사용자입니다.")
    val userId: Long? = null,
    @field:Schema(
        description = "오류 응답의 code(예: INVALID_API_KEY). 정의된 에러 코드가 아니면 400입니다.",
        maxLength = ApiUsageLog.ERROR_CODE_MAX_LENGTH,
        example = "INVALID_API_KEY",
    )
    val errorCode: String? = null,
)

internal const val API_USAGE_ROUTE_MAX_LENGTH = 160

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
    @field:Schema(description = "API 키 호출의 프로젝트 id. 키가 맞지 않거나 키 호출이 아니면 null")
    val projectId: Long?,
    @field:Schema(description = "호출한 사용자 id. API 키 호출이면 프로젝트 신청자, JWT 호출이면 로그인한 사용자. 알 수 없으면 null")
    val userId: Long?,
    @field:Schema(description = "API 키 앞 11글자. 키 원문은 저장하지 않는다", maxLength = ApiUsageLog.KEY_PREFIX_MAX_LENGTH, example = "xq_ab12cd34")
    val keyPrefix: String?,
    @field:Schema(description = "오류 응답의 code. 성공이거나 code 없는 응답이면 null", maxLength = ApiUsageLog.ERROR_CODE_MAX_LENGTH)
    val errorCode: String?,
    @field:Schema(description = "접근 단계. 공개·분야 조회가 아니면 null")
    val accessLevel: UsageAccessLevel?,
) {
    companion object {
        fun from(log: ApiUsageLog) = ApiUsageLogResponse(
            requireNotNull(log.id), log.occurredAt.toInstant(ZoneOffset.UTC), UsageHttpMethod.valueOf(log.method),
            log.route, log.status, log.durationMs,
            log.projectId, log.userId, log.keyPrefix, log.errorCode, log.accessLevel?.let(UsageAccessLevel::valueOf),
        )
    }
}

@Schema(requiredProperties = ["id", "occurredAt", "method", "route", "status", "durationMs"])
data class ApiUsageLogDetailResponse(
    val id: Long,
    val occurredAt: Instant,
    val method: UsageHttpMethod,
    @field:Schema(maxLength = 160)
    val route: String,
    val status: Int,
    @field:Min(0)
    val durationMs: Long,
    @field:Schema(description = "API 키 호출의 프로젝트 id. 키가 맞지 않거나 키 호출이 아니면 null")
    val projectId: Long?,
    @field:Schema(description = "프로젝트 이름. 프로젝트가 없거나 삭제됐으면 null")
    val projectName: String?,
    @field:Schema(description = "호출한 사용자 id. API 키 호출이면 프로젝트 신청자, JWT 호출이면 로그인한 사용자. 알 수 없으면 null")
    val userId: Long?,
    @field:Schema(description = "userId 사용자의 이름. 없거나 탈퇴했으면 null")
    val applicantName: String?,
    @field:Schema(description = "userId 사용자의 아이디. 없거나 탈퇴했으면 null")
    val applicantUsername: String?,
    @field:Schema(description = "API 키 앞 11글자. 키 원문은 저장하지 않는다", maxLength = ApiUsageLog.KEY_PREFIX_MAX_LENGTH, example = "xq_ab12cd34")
    val keyPrefix: String?,
    @field:Schema(description = "오류 응답의 code. 성공이거나 code 없는 응답이면 null", maxLength = ApiUsageLog.ERROR_CODE_MAX_LENGTH)
    val errorCode: String?,
    @field:Schema(description = "접근 단계. 공개·분야 조회가 아니면 null")
    val accessLevel: UsageAccessLevel?,
) {
    companion object {
        fun of(log: ApiUsageLog, projectName: String?, applicantName: String?, applicantUsername: String?): ApiUsageLogDetailResponse {
            val summary = ApiUsageLogResponse.from(log)
            return ApiUsageLogDetailResponse(
                summary.id, summary.occurredAt, summary.method, summary.route, summary.status, summary.durationMs,
                summary.projectId, projectName, summary.userId, applicantName, applicantUsername,
                summary.keyPrefix, summary.errorCode, summary.accessLevel,
            )
        }
    }
}
