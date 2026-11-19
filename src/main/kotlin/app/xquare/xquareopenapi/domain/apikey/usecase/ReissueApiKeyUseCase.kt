package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKeyRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeySecret
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 새 키를 발급하고 기존 키는 즉시 폐기한다. 신청 내용(서비스 이름·사용 목적)은 그대로 둔다.
@Service
class ReissueApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
) {

    @Transactional
    fun execute(userId: Long): ApiKeyResponse {
        val apiKey = apiKeyRepository.findByUserId(userId) ?: throw XquareException(ErrorCode.API_KEY_NOT_FOUND)
        val rawKey = ApiKeySecret.generate()
        apiKey.reissue(ApiKeySecret.hash(rawKey))
        return ApiKeyResponse(rawKey)
    }
}
