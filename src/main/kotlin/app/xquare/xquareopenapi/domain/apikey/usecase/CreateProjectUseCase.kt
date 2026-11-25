package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apikey.ApplicationStatus
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.CreateProjectRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectApplyResponse
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 프로젝트를 만들고 분야를 신청한다. 교내 분야가 있으면 바로 승인되므로 키를 발급해 원문을 응답에 준다.
@Service
class CreateProjectUseCase(
    private val userRepository: UserRepository,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val apiKeyEmailVerification: ApiKeyEmailVerification,
    private val clock: Clock,
) {

    @Transactional
    fun execute(userId: Long, request: CreateProjectRequest): ProjectApplyResponse {
        // 사용자 행을 잠가 같은 사용자의 프로젝트 만들기를 한 줄로 세운다. 동시에 만들어 개수 상한을 넘지 않게 하려는 것이다.
        // 잠금 조회를 트랜잭션의 첫 조회로 둔다. MySQL REPEATABLE READ에서 아래 일반 조회가 잠금을 얻은 뒤의 스냅샷을 보게 된다.
        // 사용자가 없으면 이메일 인증과 같은 403이다(이메일 인증의 사용자 조회는 잠금 조회가 올린 엔티티를 그대로 쓴다).
        userRepository.findByIdForUpdate(userId) ?: throw XquareException(ErrorCode.EMAIL_NOT_VERIFIED)
        apiKeyEmailVerification.verify(userId, request.emailVerifiedToken)
        if (projectRepository.countByUserId(userId) >= ApiProject.MAX_PER_USER) {
            throw XquareException(ErrorCode.PROJECT_LIMIT_EXCEEDED)
        }
        // 앞뒤 공백만 다른 이름이 따로 저장되지 않게 다듬어서 검사·저장한다.
        // 공백뿐인 값은 DTO의 @NotBlank가 먼저 400 INVALID_INPUT으로 막으므로 다듬은 값은 비지 않는다.
        val name = request.name.trim()
        val purpose = request.purpose.trim()
        if (projectRepository.existsByUserIdAndName(userId, name)) {
            throw XquareException(ErrorCode.PROJECT_NAME_DUPLICATED)
        }
        val now = LocalDateTime.now(clock)
        // 사용자 잠금으로 이름 검사를 차례로 지나므로 정상 흐름에서는 걸리지 않지만, 제약에 걸리면 같은 409로 바꾼다.
        val project = try {
            projectRepository.saveAndFlush(ApiProject(userId, name, purpose, now))
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(ApiProject.USER_ID_NAME_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.PROJECT_NAME_DUPLICATED)
            throw e
        }
        val fields = request.fields.distinct().map { fieldRepository.save(ApiProjectField(project.id!!, it, now)) }
        val apiKey = if (fields.any { it.status == ApplicationStatus.APPROVED }) project.issueKey(now) else null
        return ProjectApplyResponse.of(project.id!!, apiKey, fields)
    }
}
