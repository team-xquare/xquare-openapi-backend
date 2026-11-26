package app.xquare.xquareopenapi.domain.apikey.presentation

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationDetailResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationRejectRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationSearchRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationSummaryResponse
import app.xquare.xquareopenapi.domain.apikey.usecase.DecideApiApplicationUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.GetApiApplicationsUseCase
import app.xquare.xquareopenapi.global.dto.PageResponse
import app.xquare.xquareopenapi.global.error.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.extensions.Extension
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 관리자(ADMIN) 전용 분야 신청 목록·상세·승인·반려. 권한은 SecurityConfig에서 검사한다.
// 액세스 토큰으로 여는 GET이라 API 키 체인 대상이 아니다. principal은 처리한 관리자의 userId다.
@Tag(name = "API 키")
@RestController
@RequestMapping("/admin/api-applications")
class AdminApiApplicationController(
    private val getApiApplicationsUseCase: GetApiApplicationsUseCase,
    private val decideApiApplicationUseCase: DecideApiApplicationUseCase,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getApiApplications",
        summary = "관리자 분야 신청 목록 조회",
        description = "분야 신청 목록을 신청 시각 역순(같으면 id 역순)으로 페이지 조회합니다. status로 상태를 걸러 볼 수 있습니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "신청 목록")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getApplications(@ParameterObject @ModelAttribute @Valid request: ApiApplicationSearchRequest): PageResponse<ApiApplicationSummaryResponse> =
        getApiApplicationsUseCase.list(request)

    @GetMapping("/{applicationId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getApiApplication",
        summary = "관리자 분야 신청 상세 조회",
        description = "분야 신청 내용, 처리 기록, 프로젝트 정보 및 신청자 정보를 조회합니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "신청 상세")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "APPLICATION_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getApplication(@Parameter(description = "신청 id(api_project_fields.id)", example = "1") @PathVariable applicationId: Long): ApiApplicationDetailResponse =
        getApiApplicationsUseCase.detail(applicationId)

    @PostMapping("/{applicationId}/approve", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "approveApiApplication",
        summary = "관리자 분야 신청 승인",
        description = "대기(PENDING) 중인 분야 신청을 승인합니다. 키는 첫 분야 신청 때 발급되므로 상태만 바꿉니다(키 없는 예전 프로젝트만 이때 발급하고 원문은 반환하지 않습니다). 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "승인됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "APPLICATION_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "APPLICATION_ALREADY_DECIDED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun approve(
        @AuthenticationPrincipal adminId: Long,
        @Parameter(description = "신청 id(api_project_fields.id)", example = "1") @PathVariable applicationId: Long,
    ): ApiApplicationDetailResponse =
        decideApiApplicationUseCase.approve(adminId, applicationId)

    @PostMapping("/{applicationId}/reject", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "rejectApiApplication",
        summary = "관리자 분야 신청 반려",
        description = "대기(PENDING) 중인 분야 신청을 사유와 함께 반려합니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "반려됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "APPLICATION_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "APPLICATION_ALREADY_DECIDED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun reject(
        @AuthenticationPrincipal adminId: Long,
        @Parameter(description = "신청 id(api_project_fields.id)", example = "1") @PathVariable applicationId: Long,
        @RequestBody @Valid request: ApiApplicationRejectRequest,
    ): ApiApplicationDetailResponse =
        decideApiApplicationUseCase.reject(adminId, applicationId, request.reason)
}
