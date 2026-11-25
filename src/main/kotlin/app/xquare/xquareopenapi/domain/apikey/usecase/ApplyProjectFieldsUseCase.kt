package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProjectField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApplicationStatus
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApplyProjectFieldsRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectApplyResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 내 프로젝트에 분야를 더 신청한다. 반려된 분야는 다시 신청할 수 있다.
// 이번 신청으로 프로젝트의 첫 승인 분야가 생기면(교내 분야) 키를 발급해 원문을 응답에 준다.
@Service
class ApplyProjectFieldsUseCase(
    private val projectFinder: MyProjectFinder,
    private val fieldRepository: ApiProjectFieldRepository,
    private val apiKeyEmailVerification: ApiKeyEmailVerification,
    private val clock: Clock,
) {

    @Transactional
    fun execute(userId: Long, projectId: Long, request: ApplyProjectFieldsRequest): ProjectApplyResponse {
        val project = projectFinder.lock(userId, projectId)
        apiKeyEmailVerification.verify(userId, request.emailVerifiedToken)
        val now = LocalDateTime.now(clock)
        val applied = fieldRepository.findAllByProjectIdOrderByIdAsc(projectId).associateBy { it.field }
        // 프로젝트 행을 잠근 뒤라 같은 프로젝트의 신청은 아래 검사를 차례로 지나므로 정상 흐름에서는 제약에 걸리지 않는다.
        // 그래도 같은 분야가 unique 제약에 걸리면 같은 409로 바꾼다. id가 IDENTITY라 새 행은 save에서 바로 insert되므로 save부터 감싼다.
        try {
            request.fields.distinct().forEach { field ->
                val current = applied[field]
                when {
                    current == null -> fieldRepository.save(ApiProjectField(projectId, field, now))
                    current.status == ApplicationStatus.REJECTED -> current.resubmit(now)
                    else -> throw XquareException(ErrorCode.FIELD_ALREADY_APPLIED)
                }
            }
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(ApiProjectField.FIELD_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.FIELD_ALREADY_APPLIED)
            throw e
        }
        val fields = fieldRepository.findAllByProjectIdOrderByIdAsc(projectId)
        val firstApproval = !project.keyIssued && fields.any { it.status == ApplicationStatus.APPROVED }
        val apiKey = if (firstApproval) project.issueKey(now) else null
        return ProjectApplyResponse.of(projectId, apiKey, fields)
    }
}
