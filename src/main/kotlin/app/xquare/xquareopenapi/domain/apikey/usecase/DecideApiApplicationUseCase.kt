package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apikey.ApplicationStatus
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationDetailResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDateTime

// 관리자가 대기(PENDING) 중인 분야 신청을 승인·반려한다. 이미 처리된 신청이면 409 APPLICATION_ALREADY_DECIDED.
// 승인이 프로젝트의 첫 승인이면 키를 발급하되 해시만 남긴다. 원문은 사용자가 재발급 API로 받는다(명세 4.1).
@Service
class DecideApiApplicationUseCase(
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val detailReader: ApiApplicationDetailReader,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    fun approve(adminId: Long, applicationId: Long): ApiApplicationDetailResponse =
        decide(applicationId) { field, project, now ->
            field.approve(adminId, now)
            if (!project.keyIssued) project.issueKey(now)
        }

    fun reject(adminId: Long, applicationId: Long, reason: String): ApiApplicationDetailResponse =
        decide(applicationId) { field, _, now -> field.reject(adminId, reason, now) }

    // 같은 프로젝트를 바꾸는 다른 요청(분야 신청·재발급·삭제·다른 승인)과 같은 순서로 프로젝트 행을 먼저 잠근다.
    // 잠글 프로젝트 id는 트랜잭션 밖에서 읽는다. 트랜잭션 안에서 잠금 전에 일반 조회를 하면 MySQL REPEATABLE READ가
    // 그때 스냅샷을 만들어, 잠금을 기다리는 사이 다른 관리자가 처리한 상태를 못 보고 두 번 처리할 수 있다.
    private fun decide(
        applicationId: Long,
        action: (ApiProjectField, ApiProject, LocalDateTime) -> Unit,
    ): ApiApplicationDetailResponse {
        val projectId = fieldRepository.findByIdOrNull(applicationId)?.projectId
            ?: throw XquareException(ErrorCode.APPLICATION_NOT_FOUND)
        return transaction.execute {
            // 그 사이 프로젝트가 지워졌으면 신청도 함께 지워졌다.
            val project = projectRepository.findByIdForUpdate(projectId)
                ?: throw XquareException(ErrorCode.APPLICATION_NOT_FOUND)
            val field = fieldRepository.findByIdOrNull(applicationId)
                ?: throw XquareException(ErrorCode.APPLICATION_NOT_FOUND)
            if (field.status != ApplicationStatus.PENDING) throw XquareException(ErrorCode.APPLICATION_ALREADY_DECIDED)
            action(field, project, LocalDateTime.now(clock))
            detailReader.read(field, project)
        }!!
    }
}
