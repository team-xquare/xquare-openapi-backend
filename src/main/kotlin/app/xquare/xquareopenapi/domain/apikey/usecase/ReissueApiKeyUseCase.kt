package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKeyRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeySecret
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ReissueApiKeyRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 키 값만 새로 발급하고 기존 키는 즉시 폐기한다. 신청한 분야와 신청 기록은 그대로 둔다.
@Service
class ReissueApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyEmailVerification: ApiKeyEmailVerification,
) {

    @Transactional
    fun execute(userId: Long, request: ReissueApiKeyRequest): ApiKeyResponse {
        apiKeyEmailVerification.verify(userId, request.emailVerifiedToken)
        // 신청 흐름처럼 키 행을 잠그고 다시 읽는다. 잠그지 않으면 키 삭제와 겹칠 때 해시 update가 0행이 되어
        // Hibernate가 stale state 예외로 500을 낸다. 잠그기 전에 삭제가 끝났으면 잠금 조회가 null이라 404다.
        val apiKey = apiKeyRepository.findByUserId(userId)?.let { apiKeyRepository.findByIdForUpdate(it.id!!) }
            ?: throw XquareException(ErrorCode.API_KEY_NOT_FOUND)
        val rawKey = ApiKeySecret.generate()
        apiKey.reissue(ApiKeySecret.hash(rawKey))
        return ApiKeyResponse(rawKey)
    }
}
