package app.xquare.xquareopenapi.domain.apikey.presentation

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyEmailVerificationRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyRenewResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApplyProjectFieldsRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.CreateProjectRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectApplyResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectDetailResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectSummaryResponse
import app.xquare.xquareopenapi.domain.apikey.usecase.ApplyProjectFieldsUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.CreateProjectUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.DeleteProjectUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.GetMyProjectsUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.ReissueProjectApiKeyUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.RenewProjectApiKeyUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 로그인한 사용자 본인의 프로젝트만 다룬다. principal은 JwtAuthenticationFilter가 넣은 userId다.
@Tag(name = "API 키")
@RestController
@RequestMapping("/projects")
class ProjectController(
    private val createProjectUseCase: CreateProjectUseCase,
    private val applyProjectFieldsUseCase: ApplyProjectFieldsUseCase,
    private val getMyProjectsUseCase: GetMyProjectsUseCase,
    private val reissueProjectApiKeyUseCase: ReissueProjectApiKeyUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
    private val renewProjectApiKeyUseCase: RenewProjectApiKeyUseCase,
) {

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createProject",
        summary = "프로젝트 만들기",
        description = "이름만으로 빈 프로젝트(서비스)를 만듭니다. 분야·사용 목적·이메일 인증은 분야 신청 API에서 받고, 키도 첫 분야 신청 때 발급됩니다. 사용자당 최대 5개입니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "201", description = "생성됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "PROJECT_LIMIT_EXCEEDED / PROJECT_NAME_DUPLICATED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun create(
        @AuthenticationPrincipal userId: Long,
        @RequestBody @Valid request: CreateProjectRequest,
    ): ProjectDetailResponse =
        createProjectUseCase.execute(userId, request)

    // 분야 신청 기록이 생기거나(반려 후 재신청이면) 다시 대기 상태가 되므로 201이다.
    @PostMapping("/{projectId}/fields", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "applyProjectFields",
        summary = "프로젝트 분야 신청",
        description = "이메일 인증 후 내 프로젝트에 분야를 신청합니다. 분야마다 사용 목적을 받습니다. 공개(PUBLIC)·교내(SCHOOL) 분야는 즉시 승인되고 제한(RESTRICTED) 분야는 관리자 승인을 기다립니다. 프로젝트에 키가 없으면 단계와 상관없이 이번 요청에서 키를 발급해 apiKey에 원문을 반환하고(60일 만료), 이미 있으면 null입니다. 승인 전 분야의 조회 API는 403 API_KEY_FIELD_FORBIDDEN입니다. 반려된(REJECTED) 분야는 재신청할 수 있으며 대기·승인된 분야는 409, 같은 분야를 두 번 넣으면 400입니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "201", description = "신청됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "EMAIL_NOT_VERIFIED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "FIELD_ALREADY_APPLIED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun applyFields(
        @AuthenticationPrincipal userId: Long,
        @Parameter(description = "프로젝트 id", example = "1") @PathVariable projectId: Long,
        @RequestBody @Valid request: ApplyProjectFieldsRequest,
    ): ProjectApplyResponse =
        applyProjectFieldsUseCase.execute(userId, projectId, request)

    // 액세스 토큰으로 여는 GET이라 API 키 체인 대상이 아니다(ApiKeyReadEndpointCoverageTest의 예외 목록).
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getMyProjects",
        summary = "내 프로젝트 목록 조회",
        description = "로그인한 사용자의 프로젝트 목록을 생성 순으로 조회합니다. 키 원문은 반환하지 않습니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "프로젝트 목록")
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getMine(@AuthenticationPrincipal userId: Long): List<ProjectSummaryResponse> =
        getMyProjectsUseCase.list(userId)

    @GetMapping("/{projectId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getMyProject",
        summary = "내 프로젝트 상세 조회",
        description = "로그인한 사용자의 프로젝트 상세 정보(사용 목적, 생성 시각, 키 발급/만료 정보, 분야 상태)를 조회합니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "프로젝트 상세")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getOne(
        @AuthenticationPrincipal userId: Long,
        @Parameter(description = "프로젝트 id", example = "1") @PathVariable projectId: Long,
    ): ProjectDetailResponse =
        getMyProjectsUseCase.detail(userId, projectId)

    @PostMapping("/{projectId}/api-key/reissue", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "reissueProjectApiKey",
        summary = "프로젝트 API 키 재발급",
        description = "이메일 인증 후 프로젝트의 API 키를 새로 발급하고 만료 시각을 지금부터 60일 뒤로 설정합니다. 기존 키는 즉시 폐기됩니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "재발급 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "EMAIL_NOT_VERIFIED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "API_KEY_NOT_ISSUED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun reissue(
        @AuthenticationPrincipal userId: Long,
        @Parameter(description = "프로젝트 id", example = "1") @PathVariable projectId: Long,
        @RequestBody @Valid request: ApiKeyEmailVerificationRequest,
    ): ApiKeyResponse =
        reissueProjectApiKeyUseCase.execute(userId, projectId, request)

    @PostMapping("/{projectId}/api-key/renew", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "renewProjectApiKey",
        summary = "프로젝트 API 키 만료 갱신",
        description = "이메일 인증 후 기존 API 키 값과 issuedAt은 유지한 채 만료 시각(expiresAt)만 지금부터 60일 뒤로 연장합니다. 이미 만료된 키도 갱신할 수 있습니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "갱신 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "EMAIL_NOT_VERIFIED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "API_KEY_NOT_ISSUED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun renew(
        @AuthenticationPrincipal userId: Long,
        @Parameter(description = "프로젝트 id", example = "1") @PathVariable projectId: Long,
        @RequestBody @Valid request: ApiKeyEmailVerificationRequest,
    ): ApiKeyRenewResponse =
        renewProjectApiKeyUseCase.execute(userId, projectId, request)

    @DeleteMapping("/{projectId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "deleteProject",
        summary = "프로젝트 삭제",
        description = "프로젝트와 분야 신청 기록, API 키를 모두 삭제합니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "삭제됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun delete(
        @AuthenticationPrincipal userId: Long,
        @Parameter(description = "프로젝트 id", example = "1") @PathVariable projectId: Long,
    ): MessageResponse {
        deleteProjectUseCase.execute(userId, projectId)
        return MessageResponse("삭제되었습니다.")
    }
}
