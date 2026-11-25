package app.xquare.xquareopenapi.domain.apikey.usecase

import app.xquare.xquareopenapi.domain.apikey.ApiProjectFieldRepository
import app.xquare.xquareopenapi.domain.apikey.ApiProjectRepository
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationDetailResponse
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationSearchRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApiApplicationSummaryResponse
import app.xquare.xquareopenapi.global.dto.PageResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
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
    private val detailReader: ApiApplicationDetailReader,
) {

    // 상태로 거르고(없으면 전체) 최근 신청부터 보여 준다. 신청 시각이 같으면 id 역순이라 페이지를 넘겨도 순서가 흔들리지 않는다.
    @Transactional(readOnly = true)
    fun list(request: ApiApplicationSearchRequest): PageResponse<ApiApplicationSummaryResponse> {
        val pageable = PageRequest.of(request.page, request.size, Sort.by(Sort.Order.desc("appliedAt"), Sort.Order.desc("id")))
        val fields = fieldRepository.search(request.status, pageable)
        // 페이지가 비면 붙일 프로젝트가 없으므로 프로젝트를 읽지 않는다.
        if (fields.content.isEmpty()) {
            return PageResponse(emptyList(), fields.number, fields.size, fields.totalElements, fields.totalPages)
        }
        // 신청은 프로젝트 id만 들고 있어서, 한 페이지의 프로젝트를 한 번에 읽어 이름과 신청자를 붙인다.
        // 분야는 프로젝트와 같은 트랜잭션에서 지워지고 REPEATABLE READ라 같은 스냅샷을 보므로 프로젝트는 항상 있다.
        val projects = projectRepository.findAllById(fields.content.map { it.projectId }.toSet()).associateBy { it.id!! }
        return PageResponse.of(fields.map { ApiApplicationSummaryResponse.of(it, projects.getValue(it.projectId)) })
    }

    @Transactional(readOnly = true)
    fun detail(applicationId: Long): ApiApplicationDetailResponse {
        val field = fieldRepository.findByIdOrNull(applicationId) ?: throw XquareException(ErrorCode.APPLICATION_NOT_FOUND)
        return detailReader.read(field, projectRepository.findById(field.projectId).orElseThrow())
    }
}
