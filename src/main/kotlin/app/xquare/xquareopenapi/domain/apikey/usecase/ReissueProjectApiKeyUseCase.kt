package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyEmailVerificationRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 프로젝트의 키 값을 새로 발급하고 기존 키는 즉시 폐기한다. 분야와 승인 상태는 그대로 둔다.
@Service
class ReissueProjectApiKeyUseCase(
    private val projectFinder: MyProjectFinder,
    private val apiKeyEmailVerification: ApiKeyEmailVerification,
    private val clock: Clock,
) {

    @Transactional
    fun execute(userId: Long, projectId: Long, request: ApiKeyEmailVerificationRequest): ApiKeyResponse {
        val project = projectFinder.lock(userId, projectId)
        apiKeyEmailVerification.verify(userId, request.emailVerifiedToken)
        // 승인된 분야가 없어 아직 발급 전이면 다시 발급할 키도 없다.
        if (!project.keyIssued) throw XquareException(ErrorCode.API_KEY_NOT_ISSUED)
        val rawKey = project.issueKey(LocalDateTime.now(clock))
        return ApiKeyResponse.of(rawKey, project)
    }
}
