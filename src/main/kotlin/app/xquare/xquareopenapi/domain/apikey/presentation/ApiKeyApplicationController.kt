package app.xquare.xquareopenapi.domain.apikey.presentation

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationDetailResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationSearchRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationSummaryResponse
import app.xquare.xquareopenapi.domain.apikey.usecase.GetApiKeyApplicationUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.GetApiKeyApplicationsUseCase
import app.xquare.xquareopenapi.global.dto.PageResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 관리자(ADMIN) 전용. 권한은 SecurityConfig에서 검사한다. 액세스 토큰으로 여는 GET이라 API 키 체인 대상이 아니다.
@RestController
@RequestMapping("/api-key-applications")
class ApiKeyApplicationController(
    private val getApiKeyApplicationsUseCase: GetApiKeyApplicationsUseCase,
    private val getApiKeyApplicationUseCase: GetApiKeyApplicationUseCase,
) {

    @GetMapping
    fun getApplications(
        @ModelAttribute @Valid request: ApiKeyApplicationSearchRequest,
    ): PageResponse<ApiKeyApplicationSummaryResponse> =
        getApiKeyApplicationsUseCase.execute(request)

    @GetMapping("/{applicationId}")
    fun getApplication(@PathVariable applicationId: Long): ApiKeyApplicationDetailResponse =
        getApiKeyApplicationUseCase.execute(applicationId)
}
