package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeyRepository
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationSearchRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationSummaryResponse
import app.xquare.xquareopenapi.global.dto.PageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 관리자용 활용신청 목록. 분야·구분으로 거르고 최근 신청부터 보여 준다.
@Service
class GetApiKeyApplicationsUseCase(
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
    private val apiKeyRepository: ApiKeyRepository,
) {

    // 신청 시각이 같으면 id 역순이라 페이지를 넘겨도 순서가 흔들리지 않는다.
    @Transactional(readOnly = true)
    fun execute(request: ApiKeyApplicationSearchRequest): PageResponse<ApiKeyApplicationSummaryResponse> {
        val pageable = PageRequest.of(request.page, request.size, Sort.by(Sort.Order.desc("appliedAt"), Sort.Order.desc("id")))
        val applications = apiKeyApplicationRepository.search(request.field, request.type, pageable)
        // 신청 기록은 키 id만 들고 있어서, 한 페이지의 키를 한 번에 읽어 신청자 userId를 붙인다.
        val userIdByApiKeyId = apiKeyRepository.findAllById(applications.content.map { it.apiKeyId }.toSet())
            .associate { it.id!! to it.userId }
        return PageResponse.of(applications.map { ApiKeyApplicationSummaryResponse.of(it, userIdByApiKeyId.getValue(it.apiKeyId)) })
    }
}
