package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKey
import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplication
import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationType
import app.xquare.xquareopenapi.domain.apikey.ApiKeyRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeySecret
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyApplicationResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 분야별 활용신청. 자동 승인이다. 키가 없으면 첫 신청으로 키를 발급하고, 있으면 그 키에 분야만 더한다.
@Service
class ApplyApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
    private val apiKeyEmailVerification: ApiKeyEmailVerification,
) {

    @Transactional
    fun execute(userId: Long, request: ApiKeyApplicationRequest): ApiKeyApplicationResponse {
        apiKeyEmailVerification.verify(userId, request.emailVerifiedToken)
        // 키 행을 잠가 같은 키의 분야 추가·키 삭제를 한 줄로 세운다. 그 사이 키가 지워졌으면 첫 신청으로 처리한다.
        val apiKey = apiKeyRepository.findByUserId(userId)?.let { apiKeyRepository.findByIdForUpdate(it.id!!) }
        return if (apiKey == null) applyFirst(userId, request) else addField(apiKey, request)
    }

    private fun applyFirst(userId: Long, request: ApiKeyApplicationRequest): ApiKeyApplicationResponse {
        val rawKey = ApiKeySecret.generate()
        // 같은 사용자의 첫 신청이 동시에 들어와 먼저 저장된 키가 있으면 unique 제약에 걸린다. 다시 신청하면 분야 추가가 된다.
        val apiKey = try {
            apiKeyRepository.saveAndFlush(ApiKey(userId, ApiKeySecret.hash(rawKey)))
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(ApiKey.USER_ID_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.API_KEY_ALREADY_EXISTS)
            throw e
        }
        save(apiKey, request, ApiKeyApplicationType.FIRST)
        return ApiKeyApplicationResponse(ApiKeyApplicationType.FIRST, rawKey)
    }

    // 키 행을 잠근 뒤라 같은 키의 신청이 동시에 와도 이 검사를 차례로 지난다.
    private fun addField(apiKey: ApiKey, request: ApiKeyApplicationRequest): ApiKeyApplicationResponse {
        if (apiKeyApplicationRepository.existsByApiKeyIdAndField(apiKey.id!!, request.field)) {
            throw XquareException(ErrorCode.API_KEY_FIELD_ALREADY_APPLIED)
        }
        save(apiKey, request, ApiKeyApplicationType.ADD_FIELD)
        return ApiKeyApplicationResponse(ApiKeyApplicationType.ADD_FIELD, null)
    }

    private fun save(apiKey: ApiKey, request: ApiKeyApplicationRequest, type: ApiKeyApplicationType) {
        apiKeyApplicationRepository.save(
            ApiKeyApplication(apiKey.id!!, request.field, type, request.serviceName, request.purpose),
        )
    }
}
