package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProjectField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApplicationStatus
import app.xquare.xquareopenapi.domain.apikey.ApplicationType
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

// 내 프로젝트에 분야를 신청한다. 분야마다 사용 목적을 받고, 반려된 분야는 다시 신청할 수 있다.
// 프로젝트에 키가 아직 없으면 단계와 상관없이 이번 신청에서 키를 발급해 원문을 응답에 준다.
// 승인 전(대기·반려) 분야의 조회 API는 키가 있어도 403 API_KEY_FIELD_FORBIDDEN이다.
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
        // 같은 분야가 두 번 들어오면 어느 사용 목적으로 저장할지 정할 수 없다.
        if (request.fields.distinctBy { it.field }.size != request.fields.size) throw XquareException(ErrorCode.INVALID_INPUT)
        val now = LocalDateTime.now(clock)
        val applied = fieldRepository.findAllByProjectIdOrderByIdAsc(projectId).associateBy { it.field }
        // 프로젝트에 신청 기록이 하나도 없을 때 들어온 요청의 분야가 첫 신청이다.
        val type = if (applied.isEmpty()) ApplicationType.FIRST else ApplicationType.ADDITIONAL
        // 프로젝트 행을 잠근 뒤라 같은 프로젝트의 신청은 아래 검사를 차례로 지나므로 정상 흐름에서는 제약에 걸리지 않는다.
        // 그래도 같은 분야가 unique 제약에 걸리면 같은 409로 바꾼다. id가 IDENTITY라 새 행은 save에서 바로 insert되므로 save부터 감싼다.
        try {
            request.fields.forEach { application ->
                // 앞뒤 공백은 다듬어 저장한다. 공백뿐인 목적은 DTO의 @NotBlank가 먼저 400으로 막는다.
                val purpose = application.purpose.trim()
                val current = applied[application.field]
                when {
                    current == null -> fieldRepository.save(ApiProjectField(projectId, application.field, purpose, type, now))
                    current.status == ApplicationStatus.REJECTED -> current.resubmit(purpose, now)
                    else -> throw XquareException(ErrorCode.FIELD_ALREADY_APPLIED)
                }
            }
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(ApiProjectField.FIELD_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.FIELD_ALREADY_APPLIED)
            throw e
        }
        val fields = fieldRepository.findAllByProjectIdOrderByIdAsc(projectId)
        val apiKey = if (project.keyIssued) null else project.issueKey(now)
        return ProjectApplyResponse.of(projectId, apiKey, fields)
    }
}
