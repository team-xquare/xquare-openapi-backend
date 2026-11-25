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
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal userId: Long,
        @RequestBody @Valid request: CreateProjectRequest,
    ): ProjectApplyResponse =
        createProjectUseCase.execute(userId, request)

    // 분야 신청 기록이 생기거나(반려 후 재신청이면) 다시 대기 상태가 되므로 201이다.
    @PostMapping("/{projectId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    fun applyFields(
        @AuthenticationPrincipal userId: Long,
        @PathVariable projectId: Long,
        @RequestBody @Valid request: ApplyProjectFieldsRequest,
    ): ProjectApplyResponse =
        applyProjectFieldsUseCase.execute(userId, projectId, request)

    // 액세스 토큰으로 여는 GET이라 API 키 체인 대상이 아니다(ApiKeyReadEndpointCoverageTest의 예외 목록).
    @GetMapping
    fun getMine(@AuthenticationPrincipal userId: Long): List<ProjectSummaryResponse> =
        getMyProjectsUseCase.list(userId)

    @GetMapping("/{projectId}")
    fun getOne(@AuthenticationPrincipal userId: Long, @PathVariable projectId: Long): ProjectDetailResponse =
        getMyProjectsUseCase.detail(userId, projectId)

    @PostMapping("/{projectId}/api-key/reissue")
    fun reissue(
        @AuthenticationPrincipal userId: Long,
        @PathVariable projectId: Long,
        @RequestBody @Valid request: ApiKeyEmailVerificationRequest,
    ): ApiKeyResponse =
        reissueProjectApiKeyUseCase.execute(userId, projectId, request)

    @PostMapping("/{projectId}/api-key/renew")
    fun renew(
        @AuthenticationPrincipal userId: Long,
        @PathVariable projectId: Long,
        @RequestBody @Valid request: ApiKeyEmailVerificationRequest,
    ): ApiKeyRenewResponse =
        renewProjectApiKeyUseCase.execute(userId, projectId, request)

    @DeleteMapping("/{projectId}")
    fun delete(@AuthenticationPrincipal userId: Long, @PathVariable projectId: Long): MessageResponse {
        deleteProjectUseCase.execute(userId, projectId)
        return MessageResponse("삭제되었습니다.")
    }
}
