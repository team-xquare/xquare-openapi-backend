package app.xquare.xquareopenapi.domain.apiusage.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLogRepository
import app.xquare.xquareopenapi.domain.apiusage.presentation.ApiUsageLogDetailResponse
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 관리자 사용 기록 상세. 기록에는 id만 있으므로 프로젝트 이름과 사용자 이름·아이디를 지금 값으로 붙인다.
// 프로젝트나 사용자가 지워졌으면 이름은 null이고 기록의 id는 그대로 보여 준다.
@Service
class GetApiUsageLogUseCase(
    private val repository: ApiUsageLogRepository,
    private val projectRepository: ApiProjectRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun execute(logId: Long): ApiUsageLogDetailResponse {
        val log = repository.findByIdOrNull(logId) ?: throw XquareException(ErrorCode.API_USAGE_LOG_NOT_FOUND)
        val projectId = log.projectId
        val project = if (projectId != null) projectRepository.findByIdOrNull(projectId) else null
        val userId = log.userId
        val user = if (userId != null) userRepository.findByIdOrNull(userId) else null
        return ApiUsageLogDetailResponse.of(log, project?.name, user?.name, user?.username)
    }
}
