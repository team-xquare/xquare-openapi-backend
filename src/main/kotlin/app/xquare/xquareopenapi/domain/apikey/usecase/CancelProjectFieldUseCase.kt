package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApplicationStatus
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 내 프로젝트의 승인 대기(PENDING) 중인 분야 신청을 취소한다. 신청 행을 지우므로 같은 분야를 바로 다시 신청할 수 있다.
// 승인·반려된 신청은 관리자가 이미 처리했으므로 409 APPLICATION_NOT_PENDING, 신청한 적 없는 분야는 404 APPLICATION_NOT_FOUND.
@Service
class CancelProjectFieldUseCase(
    private val projectFinder: MyProjectFinder,
    private val fieldRepository: ApiProjectFieldRepository,
) {

    @Transactional
    fun execute(userId: Long, projectId: Long, field: ApiKeyField) {
        // 프로젝트 행을 먼저 잠가 같은 프로젝트의 승인·반려와 한 줄로 세운다. 관리자가 승인하는 사이에 취소돼 승인된 신청이 지워지지 않게 하려는 것이다.
        projectFinder.lock(userId, projectId)
        val application = fieldRepository.findAllByProjectIdOrderByIdAsc(projectId).firstOrNull { it.field == field }
            ?: throw XquareException(ErrorCode.APPLICATION_NOT_FOUND)
        if (application.status != ApplicationStatus.PENDING) throw XquareException(ErrorCode.APPLICATION_NOT_PENDING)
        // 프로젝트 잠금으로 같은 신청의 동시 취소가 차례로 지나가서, 늦은 쪽은 위에서 404가 된다. 그래서 조회 후 삭제해도 0행 삭제가 생기지 않는다.
        fieldRepository.delete(application)
    }
}
