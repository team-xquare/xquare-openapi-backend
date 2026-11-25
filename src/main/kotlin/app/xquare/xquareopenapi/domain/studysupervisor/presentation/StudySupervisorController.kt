package app.xquare.xquareopenapi.domain.studysupervisor.presentation

import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorDayRequest
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorDayResponse
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorRangeResponse
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorRequest
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorSearchRequest
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.CreateStudySupervisorUseCase
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.DeleteStudySupervisorUseCase
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.GetStudySupervisorByDayUseCase
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.GetStudySupervisorsUseCase
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.UpdateStudySupervisorUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
import app.xquare.xquareopenapi.global.error.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.extensions.Extension
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

// 권한(조회는 USER, 등록·수정·삭제는 ADMIN)은 SecurityConfig에서 검사한다.
// 명세에 단건 조회가 없어서 GET /study-supervisors/{id}는 만들지 않았다. Location은 수정·삭제할 URI다.
@Tag(name = "자습 감독")
@RestController
@RequestMapping("/study-supervisors")
class StudySupervisorController(
    private val getStudySupervisorsUseCase: GetStudySupervisorsUseCase,
    private val createStudySupervisorUseCase: CreateStudySupervisorUseCase,
    private val getStudySupervisorByDayUseCase: GetStudySupervisorByDayUseCase,
    private val updateStudySupervisorUseCase: UpdateStudySupervisorUseCase,
    private val deleteStudySupervisorUseCase: DeleteStudySupervisorUseCase,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getStudySupervisors",
        summary = "자습 감독 정보 전체 조회",
        description = "기간(startDate~finishDate, 최대 92일)에 해당하는 날짜별 자습 감독 배정을 반환합니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_DATE_RANGE / INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getStudySupervisors(@ParameterObject @ModelAttribute @Valid request: StudySupervisorSearchRequest): StudySupervisorRangeResponse =
        getStudySupervisorsUseCase.execute(request.startDate!!, request.finishDate!!)

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "createStudySupervisor",
        summary = "자습 감독 정보 등록",
        description = "날짜별로 층(2~4층)별 감독 교직원을 등록합니다. Location 헤더로 수정·삭제 URI를 반환합니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(
        responseCode = "201",
        description = "생성됨",
        headers = [Header(name = "Location", description = "생성된 자습 감독 정보 URI(수정·삭제용, GET 미지원)", schema = Schema(type = "string", format = "uri-reference", example = "/study-supervisors/1"))],
        content = [Content(schema = Schema(implementation = MessageResponse::class))],
    )
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "TEACHER_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "SUPERVISION_ALREADY_EXISTS", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun createStudySupervisor(@RequestBody @Valid request: StudySupervisorRequest): ResponseEntity<MessageResponse> {
        val studySupervisorId = createStudySupervisorUseCase.execute(request)
        return ResponseEntity.created(URI.create("/study-supervisors/$studySupervisorId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/day", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getStudySupervisorByDay",
        summary = "자습 감독 정보 일별로 조회",
        description = "해당 날짜의 층별 자습 감독 배정을 반환합니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getStudySupervisorByDay(@ParameterObject @ModelAttribute @Valid request: StudySupervisorDayRequest): StudySupervisorDayResponse =
        getStudySupervisorByDayUseCase.execute(request.date!!)

    @PatchMapping("/{studySupervisorId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "updateStudySupervisor",
        summary = "자습 감독 정보 수정",
        description = "자습 감독 정보를 수정합니다. supervisors는 전달한 리스트로 전체 교체됩니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "수정됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "SUPERVISION_NOT_FOUND / TEACHER_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "SUPERVISION_ALREADY_EXISTS / CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun updateStudySupervisor(
        @Parameter(description = "자습 감독 정보 id", example = "3") @PathVariable studySupervisorId: Long,
        @RequestBody @Valid request: StudySupervisorRequest,
    ): MessageResponse {
        updateStudySupervisorUseCase.execute(studySupervisorId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{studySupervisorId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "deleteStudySupervisor",
        summary = "자습 감독 정보 삭제",
        description = "자습 감독 정보를 삭제합니다. 층별 배정도 함께 지워지고 교직원은 남습니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "삭제됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "SUPERVISION_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun deleteStudySupervisor(@Parameter(description = "자습 감독 정보 id", example = "3") @PathVariable studySupervisorId: Long): MessageResponse {
        deleteStudySupervisorUseCase.execute(studySupervisorId)
        return MessageResponse("삭제되었습니다.")
    }
}
