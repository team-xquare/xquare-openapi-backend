package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectDetailResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectSummaryResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 로그인한 사용자의 프로젝트 목록·상세. 키 원문은 해시만 저장하므로 내려주지 않는다.
@Service
class GetMyProjectsUseCase(
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
    private val projectFinder: MyProjectFinder,
) {

    // 만든 순서대로. 분야는 프로젝트마다 처음 신청한 순서다. 분야는 한 번에 읽어 프로젝트별로 나눈다.
    @Transactional(readOnly = true)
    fun list(userId: Long): List<ProjectSummaryResponse> {
        val projects = projectRepository.findAllByUserIdOrderByIdAsc(userId)
        val fieldsByProject = fieldRepository.findAllByProjectIdInOrderByIdAsc(projects.map { it.id!! }).groupBy { it.projectId }
        return projects.map { ProjectSummaryResponse.of(it, fieldsByProject[it.id].orEmpty()) }
    }

    @Transactional(readOnly = true)
    fun detail(userId: Long, projectId: Long): ProjectDetailResponse {
        val project = projectFinder.find(userId, projectId)
        return ProjectDetailResponse.of(project, fieldRepository.findAllByProjectIdOrderByIdAsc(projectId))
    }
}
