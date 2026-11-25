package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.ApiProjectField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationDetailResponse
import app.xquare.xquareopenapi.domain.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

// 관리자 신청 상세 응답을 만든다. 신청자와 그 프로젝트의 분야 전체를 붙인다. 트랜잭션 안에서 불러야 한다.
@Component
class ApiApplicationDetailReader(
    private val fieldRepository: ApiProjectFieldRepository,
    private val userRepository: UserRepository,
) {
    fun read(field: ApiProjectField, project: ApiProject): ApiApplicationDetailResponse =
        ApiApplicationDetailResponse.of(
            field,
            project,
            fieldRepository.findAllByProjectIdOrderByIdAsc(project.id!!),
            userRepository.findByIdOrNull(project.userId),
        )
}
