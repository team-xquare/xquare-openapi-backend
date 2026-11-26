package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiKeyField
import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationDetailResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationSearchRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationSummaryResponse
import app.xquare.xquareopenapi.domain.user.UserRepository
import app.xquare.xquareopenapi.global.dto.PageResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.jpa.escapeLike
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 관리자용 분야 신청 목록·상세. applicationId는 api_project_fields.id다.
@Service
class GetApiApplicationsUseCase(
    private val fieldRepository: ApiProjectFieldRepository,
    private val projectRepository: ApiProjectRepository,
    private val userRepository: UserRepository,
    private val detailReader: ApiApplicationDetailReader,
) {

    // 상태·분야·단계·구분·키워드로 거르고(없으면 전체) 최근 신청부터 보여 준다. 신청 시각이 같으면 id 역순이라 페이지를 넘겨도 순서가 흔들리지 않는다.
    @Transactional(readOnly = true)
    fun list(request: ApiApplicationSearchRequest): PageResponse<ApiApplicationSummaryResponse> {
        val pageable = PageRequest.of(request.page, request.size, Sort.by(Sort.Order.desc("appliedAt"), Sort.Order.desc("id")))
        // 분야와 단계 필터를 분야 목록 하나로 합친다. 둘이 맞지 않으면(예: STUDENT + SCHOOL) 고를 분야가 없어 빈 페이지다.
        val allowed = ApiKeyField.entries.filter { (request.field == null || it == request.field) && (request.level == null || it.level == request.level) }
        if (allowed.isEmpty()) return PageResponse(emptyList(), request.page, request.size, 0, 0)
        val keywordPattern = request.keyword?.let {
            if (it.isBlank()) throw XquareException(ErrorCode.INVALID_INPUT)
            "%${it.trim().escapeLike()}%"
        }
        val fields = fieldRepository.search(request.status, request.type, allowed, keywordPattern, pageable)
        // 페이지가 비면 붙일 프로젝트가 없으므로 프로젝트를 읽지 않는다.
        if (fields.content.isEmpty()) {
            return PageResponse(emptyList(), fields.number, fields.size, fields.totalElements, fields.totalPages)
        }
        // 신청은 프로젝트 id만 들고 있어서, 한 페이지의 프로젝트를 한 번에 읽어 이름과 신청자를 붙인다.
        // 분야는 프로젝트와 같은 트랜잭션에서 지워지고 REPEATABLE READ라 같은 스냅샷을 보므로 프로젝트는 항상 있다.
        val projects = projectRepository.findAllById(fields.content.map { it.projectId }.toSet()).associateBy { it.id!! }
        val users = userRepository.findAllById(projects.values.map { it.userId }.toSet()).associateBy { it.id!! }
        return PageResponse.of(
            fields.map {
                val project = projects.getValue(it.projectId)
                ApiApplicationSummaryResponse.of(it, project, users[project.userId])
            },
        )
    }

    @Transactional(readOnly = true)
    fun detail(applicationId: Long): ApiApplicationDetailResponse {
        val field = fieldRepository.findByIdOrNull(applicationId) ?: throw XquareException(ErrorCode.APPLICATION_NOT_FOUND)
        return detailReader.read(field, projectRepository.findById(field.projectId).orElseThrow())
    }
}
