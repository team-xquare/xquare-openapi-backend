package app.xquare.xquareopenapi.domain.apiusage.presentation

import app.xquare.xquareopenapi.domain.apiusage.usecase.GetApiUsageLogsUseCase
import app.xquare.xquareopenapi.global.dto.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.extensions.Extension
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/api-usage-logs")
class ApiUsageLogController(private val getApiUsageLogs: GetApiUsageLogsUseCase) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listApiUsageLogs",
        summary = "관리자 API 사용 기록 조회",
        description = "기본 기간은 최근 24시간입니다. from과 to는 함께 지정하며 최대 31일입니다. 마이크로초로 저장된 완료 시각에 from 이상, to 미만을 적용합니다. 완료 시각, ID 순으로 내림차순 정렬합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ApiUsageLogPageSchema::class))])
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ApiUsageLogErrorSchema::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ApiUsageLogErrorSchema::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ApiUsageLogErrorSchema::class))])
    @ApiResponse(responseCode = "409", description = "CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ApiUsageLogErrorSchema::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ApiUsageLogErrorSchema::class))])
    fun list(@ParameterObject @ModelAttribute @Valid query: ApiUsageLogQuery): PageResponse<ApiUsageLogResponse> =
        getApiUsageLogs.execute(query)
}

// 관리자 기록 조회의 문서에만 적용하며 공용 런타임 DTO는 그대로 사용한다.
@Schema(requiredProperties = ["content", "page", "size", "totalCount", "totalPage"])
private data class ApiUsageLogPageSchema(
    val content: List<ApiUsageLogResponse>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
    val totalPage: Int,
)

@Schema(requiredProperties = ["code", "message"])
private data class ApiUsageLogErrorSchema(val code: String, val message: String)
