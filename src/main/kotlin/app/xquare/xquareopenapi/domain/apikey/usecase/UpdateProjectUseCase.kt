package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.CreateProjectRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectDetailResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 내 프로젝트의 이름과 서비스 URL을 바꾼다. 다른 PATCH API처럼 모든 값을 받아 통째로 바꾼다(serviceUrl을 빼면 지운다).
@Service
class UpdateProjectUseCase(
    private val projectFinder: MyProjectFinder,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
) {

    @Transactional
    fun execute(userId: Long, projectId: Long, request: CreateProjectRequest): ProjectDetailResponse {
        val project = projectFinder.lock(userId, projectId)
        // 공백뿐인 이름은 DTO의 @NotBlank가 먼저 400으로 막으므로 다듬은 값은 비지 않는다.
        val name = request.name.trim()
        // 이름을 그대로 두는 수정은 자기 자신과 겹치는 것이라 막지 않는다.
        if (name != project.name && projectRepository.existsByUserIdAndName(userId, name)) {
            throw XquareException(ErrorCode.PROJECT_NAME_DUPLICATED)
        }
        project.update(name, request.serviceUrl)
        // 같은 사용자의 다른 프로젝트 이름 변경과 겹쳐 검사를 지나쳐도 제약에 걸리면 같은 409로 바꾼다.
        try {
            projectRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(ApiProject.USER_ID_NAME_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.PROJECT_NAME_DUPLICATED)
            throw e
        }
        return ProjectDetailResponse.of(project, fieldRepository.findAllByProjectIdOrderByIdAsc(projectId))
    }
}
