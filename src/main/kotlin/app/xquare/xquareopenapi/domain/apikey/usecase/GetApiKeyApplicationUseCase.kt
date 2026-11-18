package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeyRepository
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationDetailResponse
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 관리자용 활용신청 상세. 신청 내용과 신청자, 그 키가 신청한 분야 전체를 보여 준다.
@Service
class GetApiKeyApplicationUseCase(
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun execute(applicationId: Long): ApiKeyApplicationDetailResponse {
        val application = apiKeyApplicationRepository.findByIdOrNull(applicationId)
            ?: throw XquareException(ErrorCode.API_KEY_APPLICATION_NOT_FOUND)
        // 신청 기록은 키와 같은 트랜잭션에서 지워지므로 키는 항상 있다.
        val apiKey = apiKeyRepository.findById(application.apiKeyId).orElseThrow()
        return ApiKeyApplicationDetailResponse.of(
            application,
            apiKey.userId,
            userRepository.findByIdOrNull(apiKey.userId),
            apiKeyApplicationRepository.findAllByApiKeyIdOrderByIdAsc(application.apiKeyId),
        )
    }
}
