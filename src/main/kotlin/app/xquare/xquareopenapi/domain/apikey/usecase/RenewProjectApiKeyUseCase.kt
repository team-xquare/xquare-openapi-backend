package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyEmailVerificationRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiKeyRenewResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 프로젝트 키의 만료일만 지금부터 60일 뒤로 늦춘다. 키 값은 그대로라 쓰던 키를 바꾸지 않아도 된다. 이미 만료된 키도 갱신하면 다시 쓴다.
@Service
class RenewProjectApiKeyUseCase(
    private val projectFinder: MyProjectFinder,
    private val apiKeyEmailVerification: ApiKeyEmailVerification,
    private val clock: Clock,
) {

    @Transactional
    fun execute(userId: Long, projectId: Long, request: ApiKeyEmailVerificationRequest): ApiKeyRenewResponse {
        val project = projectFinder.lock(userId, projectId)
        apiKeyEmailVerification.verify(userId, request.emailVerifiedToken)
        if (!project.keyIssued) throw XquareException(ErrorCode.API_KEY_NOT_ISSUED)
        project.renewKey(LocalDateTime.now(clock))
        return ApiKeyRenewResponse.from(project)
    }
}
