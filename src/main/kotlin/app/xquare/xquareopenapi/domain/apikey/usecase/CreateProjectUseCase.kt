package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.CreateProjectRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectDetailResponse
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 이름만으로 빈 프로젝트를 만든다. 분야·사용 목적·이메일 인증·키 발급은 분야 신청(ApplyProjectFieldsUseCase)에서 한다.
@Service
class CreateProjectUseCase(
    private val userRepository: UserRepository,
    private val projectRepository: ApiProjectRepository,
    private val clock: Clock,
) {

    @Transactional
    fun execute(userId: Long, request: CreateProjectRequest): ProjectDetailResponse {
        // 사용자 행을 잠가 같은 사용자의 프로젝트 만들기를 한 줄로 세운다. 동시에 만들어 개수 상한을 넘지 않게 하려는 것이다.
        // 잠금 조회를 트랜잭션의 첫 조회로 둔다. MySQL REPEATABLE READ에서 아래 일반 조회가 잠금을 얻은 뒤의 스냅샷을 보게 된다.
        // 토큰은 유효한데 사용자가 지워졌으면 토큰이 가리키는 계정이 없으므로 401 INVALID_TOKEN이다.
        userRepository.findByIdForUpdate(userId) ?: throw XquareException(ErrorCode.INVALID_TOKEN)
        if (projectRepository.countByUserId(userId) >= ApiProject.MAX_PER_USER) {
            throw XquareException(ErrorCode.PROJECT_LIMIT_EXCEEDED)
        }
        // 앞뒤 공백만 다른 이름이 따로 저장되지 않게 다듬어서 검사·저장한다.
        // 공백뿐인 값은 DTO의 @NotBlank가 먼저 400 INVALID_INPUT으로 막으므로 다듬은 값은 비지 않는다.
        val name = request.name.trim()
        if (projectRepository.existsByUserIdAndName(userId, name)) {
            throw XquareException(ErrorCode.PROJECT_NAME_DUPLICATED)
        }
        // 사용자 잠금으로 이름 검사를 차례로 지나므로 정상 흐름에서는 걸리지 않지만, 제약에 걸리면 같은 409로 바꾼다.
        val project = try {
            projectRepository.saveAndFlush(ApiProject(userId, name, LocalDateTime.now(clock), request.serviceUrl))
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(ApiProject.USER_ID_NAME_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.PROJECT_NAME_DUPLICATED)
            throw e
        }
        return ProjectDetailResponse.of(project, emptyList())
    }
}
