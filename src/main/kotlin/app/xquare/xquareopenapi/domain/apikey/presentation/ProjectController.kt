package app.xquare.xquareopenapi.domain.apikey.presentation

import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ApplyProjectFieldsRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.CreateProjectRequest
import app.xquare.xquareopenapi.domain.apikey.presentation.dto.ProjectApplyResponse
import app.xquare.xquareopenapi.domain.apikey.usecase.ApplyProjectFieldsUseCase
import app.xquare.xquareopenapi.domain.apikey.usecase.CreateProjectUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 로그인한 사용자 본인의 프로젝트만 다룬다. principal은 JwtAuthenticationFilter가 넣은 userId다.
@RestController
@RequestMapping("/projects")
class ProjectController(
    private val createProjectUseCase: CreateProjectUseCase,
    private val applyProjectFieldsUseCase: ApplyProjectFieldsUseCase,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal userId: Long,
        @RequestBody @Valid request: CreateProjectRequest,
    ): ProjectApplyResponse =
        createProjectUseCase.execute(userId, request)

    // 분야 신청 기록이 생기거나(반려 후 재신청이면) 다시 대기 상태가 되므로 201이다.
    @PostMapping("/{projectId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    fun applyFields(
        @AuthenticationPrincipal userId: Long,
        @PathVariable projectId: Long,
        @RequestBody @Valid request: ApplyProjectFieldsRequest,
    ): ProjectApplyResponse =
        applyProjectFieldsUseCase.execute(userId, projectId, request)
}
