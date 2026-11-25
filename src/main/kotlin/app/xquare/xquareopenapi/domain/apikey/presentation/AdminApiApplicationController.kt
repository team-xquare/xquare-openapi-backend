package app.xquare.xquareopenapi.domain.apikey.presentation

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationDetailResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationRejectRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationSearchRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationSummaryResponse
import app.xquare.xquareopenapi.domain.apikey.usecase.DecideApiApplicationUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.GetApiApplicationsUseCase
import app.xquare.xquareopenapi.global.dto.PageResponse
import jakarta.validation.Valid
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
@RestController
@RequestMapping("/admin/api-applications")
class AdminApiApplicationController(
    private val getApiApplicationsUseCase: GetApiApplicationsUseCase,
    private val decideApiApplicationUseCase: DecideApiApplicationUseCase,
) {

    @GetMapping
    fun getApplications(@ModelAttribute @Valid request: ApiApplicationSearchRequest): PageResponse<ApiApplicationSummaryResponse> =
        getApiApplicationsUseCase.list(request)

    @GetMapping("/{applicationId}")
    fun getApplication(@PathVariable applicationId: Long): ApiApplicationDetailResponse =
        getApiApplicationsUseCase.detail(applicationId)

    @PostMapping("/{applicationId}/approve")
    fun approve(@AuthenticationPrincipal adminId: Long, @PathVariable applicationId: Long): ApiApplicationDetailResponse =
        decideApiApplicationUseCase.approve(adminId, applicationId)

    @PostMapping("/{applicationId}/reject")
    fun reject(
        @AuthenticationPrincipal adminId: Long,
        @PathVariable applicationId: Long,
        @RequestBody @Valid request: ApiApplicationRejectRequest,
    ): ApiApplicationDetailResponse =
        decideApiApplicationUseCase.reject(adminId, applicationId, request.reason)
}
