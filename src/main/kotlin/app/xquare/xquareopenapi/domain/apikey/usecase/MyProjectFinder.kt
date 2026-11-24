package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Component

// 로그인한 사용자의 프로젝트를 찾는다. 없거나 남의 프로젝트면 똑같이 404 PROJECT_NOT_FOUND라 남의 프로젝트가 있는지 드러나지 않는다.
@Component
class MyProjectFinder(
    private val projectRepository: ApiProjectRepository,
) {

    // 프로젝트 행을 잠그고 읽는다. 같은 프로젝트를 바꾸는 요청(분야 신청·승인·재발급·삭제)을 한 줄로 세운다.
    // 트랜잭션의 첫 조회로 불러야 MySQL REPEATABLE READ에서 뒤따르는 일반 조회가 잠금을 얻은 뒤의 스냅샷을 본다.
    fun lock(userId: Long, projectId: Long): ApiProject =
        projectRepository.findByIdForUpdate(projectId)?.takeIf { it.userId == userId }
            ?: throw XquareException(ErrorCode.PROJECT_NOT_FOUND)
}
