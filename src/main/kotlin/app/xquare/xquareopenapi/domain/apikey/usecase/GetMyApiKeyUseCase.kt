package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeyRepository
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.MyApiKeyResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 로그인한 사용자 본인 키의 발급 시각과 신청한 분야를 보여 준다. 키 원문은 저장하지 않으므로 내려줄 수 없다.
@Service
class GetMyApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
) {

    @Transactional(readOnly = true)
    fun execute(userId: Long): MyApiKeyResponse {
        val apiKey = apiKeyRepository.findByUserId(userId) ?: throw XquareException(ErrorCode.API_KEY_NOT_FOUND)
        val applications = apiKeyApplicationRepository.findAllByApiKeyIdOrderByIdAsc(apiKey.id!!)
        return MyApiKeyResponse.of(apiKey, applications)
    }
}
