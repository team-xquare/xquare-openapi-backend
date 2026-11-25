package app.xquare.xquareopenapi.domain.teacher.presentation

import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherRequest
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherResponse
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherSearchRequest
import app.xquare.xquareopenapi.domain.teacher.usecase.CreateTeacherUseCase
import app.xquare.xquareopenapi.domain.teacher.usecase.DeleteTeacherUseCase
import app.xquare.xquareopenapi.domain.teacher.usecase.GetTeacherUseCase
import app.xquare.xquareopenapi.domain.teacher.usecase.GetTeachersUseCase
import app.xquare.xquareopenapi.domain.teacher.usecase.UpdateTeacherUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
import app.xquare.xquareopenapi.global.dto.PageResponse
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
@Tag(name = "교직원")
@RestController
@RequestMapping("/teachers")
class TeacherController(
    private val getTeachersUseCase: GetTeachersUseCase,
    private val createTeacherUseCase: CreateTeacherUseCase,
    private val getTeacherUseCase: GetTeacherUseCase,
    private val updateTeacherUseCase: UpdateTeacherUseCase,
    private val deleteTeacherUseCase: DeleteTeacherUseCase,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getTeachers",
        summary = "교직원 전체 조회",
        description = "이름(정확 일치), 담당 학급, 과목, 교무실(부분 일치) 조건으로 교직원 목록을 등록 순으로 페이지 조회합니다. 인증: API 키(X-API-Key, TEACHER 분야).",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "API_KEY")])],
    )
    @ApiResponse(responseCode = "200", description = "교직원 목록")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_API_KEY / API_KEY_EXPIRED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "API_KEY_FIELD_FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getTeachers(@ParameterObject @ModelAttribute @Valid request: TeacherSearchRequest): PageResponse<TeacherResponse> =
        getTeachersUseCase.execute(request)

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "createTeacher",
        summary = "교직원 정보 등록",
        description = "교직원 정보를 등록하고 Location 헤더로 상세조회 URI를 반환합니다. 담임이 아니면 grade, classNum은 null입니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(
        responseCode = "201",
        description = "생성됨",
        headers = [Header(name = "Location", description = "생성된 교직원을 상세조회하는 URI", schema = Schema(type = "string", format = "uri-reference", example = "/teachers/1"))],
        content = [Content(schema = Schema(implementation = MessageResponse::class))],
    )
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun createTeacher(@RequestBody @Valid request: TeacherRequest): ResponseEntity<MessageResponse> {
        val teacherId = createTeacherUseCase.execute(request)
        return ResponseEntity.created(URI.create("/teachers/$teacherId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/{teacherId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getTeacher",
        summary = "교직원 조회",
        description = "교직원 id로 교직원 한 명의 정보를 조회합니다. 인증: API 키(X-API-Key, TEACHER 분야).",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "API_KEY")])],
    )
    @ApiResponse(responseCode = "200", description = "교직원 정보")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_API_KEY / API_KEY_EXPIRED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "API_KEY_FIELD_FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "TEACHER_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getTeacher(@Parameter(description = "조회할 교직원의 id", example = "1") @PathVariable teacherId: Long): TeacherResponse =
        getTeacherUseCase.execute(teacherId)

    @PatchMapping("/{teacherId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "updateTeacher",
        summary = "교직원 정보 수정",
        description = "교직원 정보를 수정합니다. 등록과 같은 모든 필드를 받아 통째로 바꿉니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "수정되었습니다.")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "TEACHER_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun updateTeacher(
        @Parameter(description = "수정할 교직원의 id", example = "1") @PathVariable teacherId: Long,
        @RequestBody @Valid request: TeacherRequest,
    ): MessageResponse {
        updateTeacherUseCase.execute(teacherId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{teacherId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "deleteTeacher",
        summary = "교직원 정보 삭제",
        description = "교직원 정보를 삭제합니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "삭제되었습니다.")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "TEACHER_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun deleteTeacher(@Parameter(description = "삭제할 교직원의 id", example = "1") @PathVariable teacherId: Long): MessageResponse {
        deleteTeacherUseCase.execute(teacherId)
        return MessageResponse("삭제되었습니다.")
    }
}
