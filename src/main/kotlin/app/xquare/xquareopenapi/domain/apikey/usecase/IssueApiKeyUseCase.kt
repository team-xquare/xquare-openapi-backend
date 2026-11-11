package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKey
import app.xquare.xquareopenapi.domain.apikey.ApiKeyRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeySecret
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 활용신청은 자동 승인이라 신청을 받으면 바로 키를 발급한다.
@Service
class IssueApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
) {

    @Transactional
    fun execute(userId: Long, request: ApiKeyRequest): ApiKeyResponse {
        if (apiKeyRepository.existsByUserId(userId)) {
            throw XquareException(ErrorCode.API_KEY_ALREADY_EXISTS)
        }
        val rawKey = ApiKeySecret.generate()
        // 위의 중복 검사와 저장 사이에 같은 사용자의 신청이 먼저 저장되면 unique 제약에 걸린다. 이때도 409로 응답한다.
        try {
            apiKeyRepository.saveAndFlush(ApiKey(userId, request.serviceName, request.purpose, ApiKeySecret.hash(rawKey)))
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(ApiKey.USER_ID_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.API_KEY_ALREADY_EXISTS)
            throw e
        }
        return ApiKeyResponse(rawKey)
    }
}
