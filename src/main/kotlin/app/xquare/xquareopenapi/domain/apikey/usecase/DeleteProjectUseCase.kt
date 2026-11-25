package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 프로젝트와 분야 신청 기록, 키를 모두 지운다(하드 삭제). 키도 프로젝트 행에 있으므로 바로 못 쓰게 된다.
// 명세가 이메일 인증을 신청·갱신·재발급에만 요구해서 삭제에는 묻지 않는다.
@Service
class DeleteProjectUseCase(
    private val projectFinder: MyProjectFinder,
    private val projectRepository: ApiProjectRepository,
    private val fieldRepository: ApiProjectFieldRepository,
) {

    // 프로젝트 행을 잠근 뒤 지운다. 같은 프로젝트를 동시에 지우면 늦은 쪽은 잠금을 기다렸다가 행이 없어 404이고,
    // 분야 신청·승인과 겹쳐도 그쪽이 잠금에서 기다리므로 지운 프로젝트에 분야가 남지 않는다.
    @Transactional
    fun execute(userId: Long, projectId: Long) {
        val project = projectFinder.lock(userId, projectId)
        fieldRepository.deleteAllByProjectId(projectId)
        projectRepository.delete(project)
    }
}
