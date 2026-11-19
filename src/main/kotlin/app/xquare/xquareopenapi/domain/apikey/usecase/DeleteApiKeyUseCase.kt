package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationRepository
import app.xquare.xquareopenapi.domain.apikey.ApiKeyRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 로그인한 사용자의 키와 모든 신청 기록을 지워 처음 상태로 되돌린다. 다시 신청하면 첫 신청(FIRST)으로 새 키를 받는다.
@Service
class DeleteApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
) {

    @Transactional
    fun execute(userId: Long) {
        val apiKey = apiKeyRepository.findByUserId(userId) ?: throw XquareException(ErrorCode.API_KEY_NOT_FOUND)
        // 키 행을 먼저 지워 잠근다. 분야 추가는 키 행 잠금(findByIdForUpdate)에서 이 삭제가 끝나길 기다리므로 지운 키에 신청 기록이 남지 않는다.
        // 같은 키를 동시에 지우면 늦은 쪽은 0행 삭제라 404다.
        if (apiKeyRepository.deleteByIdReturningCount(apiKey.id!!) == 0) {
            throw XquareException(ErrorCode.API_KEY_NOT_FOUND)
        }
        apiKeyApplicationRepository.deleteAllByApiKeyId(apiKey.id!!)
    }
}
