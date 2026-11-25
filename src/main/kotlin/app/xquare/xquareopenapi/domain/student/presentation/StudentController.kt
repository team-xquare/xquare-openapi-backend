package app.xquare.xquareopenapi.domain.student.presentation

import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentRequest
import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentResponse
import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentSearchRequest
import app.xquare.xquareopenapi.domain.student.usecase.CreateStudentUseCase
import app.xquare.xquareopenapi.domain.student.usecase.DeleteStudentUseCase
import app.xquare.xquareopenapi.domain.student.usecase.GetStudentUseCase
import app.xquare.xquareopenapi.domain.student.usecase.GetStudentsUseCase
import app.xquare.xquareopenapi.domain.student.usecase.UpdateStudentUseCase
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
@Tag(name = "학생")
@RestController
@RequestMapping("/students")
class StudentController(
    private val getStudentsUseCase: GetStudentsUseCase,
    private val createStudentUseCase: CreateStudentUseCase,
    private val getStudentUseCase: GetStudentUseCase,
    private val updateStudentUseCase: UpdateStudentUseCase,
    private val deleteStudentUseCase: DeleteStudentUseCase,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getStudents",
        summary = "학생 전체 조회",
        description = "이름(부분 일치), 학년, 반 조건으로 학생 목록을 학번 순으로 페이지 조회합니다. 인증: API 키(X-API-Key, STUDENT 분야).",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "API_KEY")])],
    )
    @ApiResponse(responseCode = "200", description = "학생 목록")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_API_KEY / API_KEY_EXPIRED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "API_KEY_FIELD_FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getStudents(@ParameterObject @ModelAttribute @Valid request: StudentSearchRequest): PageResponse<StudentResponse> =
        getStudentsUseCase.execute(request)

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "createStudent",
        summary = "학생 정보 등록",
        description = "학생 정보를 등록하고 Location 헤더로 상세조회 URI를 반환합니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(
        responseCode = "201",
        description = "생성됨",
        headers = [Header(name = "Location", description = "등록된 학생을 상세조회하는 URI", schema = Schema(type = "string", format = "uri-reference", example = "/students/1"))],
        content = [Content(schema = Schema(implementation = MessageResponse::class))],
    )
    @ApiResponse(responseCode = "400", description = "INVALID_STUDENT_NUMBER / INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "STUDENT_NUMBER_ALREADY_EXISTS", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun createStudent(@RequestBody @Valid request: StudentRequest): ResponseEntity<MessageResponse> {
        val studentId = createStudentUseCase.execute(request)
        return ResponseEntity.created(URI.create("/students/$studentId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/{studentId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getStudent",
        summary = "학생 조회",
        description = "학생 id로 학생 한 명의 정보를 조회합니다. 인증: API 키(X-API-Key, STUDENT 분야).",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "API_KEY")])],
    )
    @ApiResponse(responseCode = "200", description = "학생 정보")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_API_KEY / API_KEY_EXPIRED", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "API_KEY_FIELD_FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getStudent(@Parameter(description = "조회할 학생의 id", example = "1") @PathVariable studentId: Long): StudentResponse =
        getStudentUseCase.execute(studentId)

    @PatchMapping("/{studentId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "updateStudent",
        summary = "학생 정보 수정",
        description = "학생 정보를 수정합니다. 등록과 같은 모든 필드를 받아 통째로 바꿉니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "수정되었습니다.")
    @ApiResponse(responseCode = "400", description = "INVALID_STUDENT_NUMBER / INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "STUDENT_NUMBER_ALREADY_EXISTS", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun updateStudent(
        @Parameter(description = "수정할 학생의 id", example = "1") @PathVariable studentId: Long,
        @RequestBody @Valid request: StudentRequest,
    ): MessageResponse {
        updateStudentUseCase.execute(studentId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{studentId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "deleteStudent",
        summary = "학생 정보 삭제",
        description = "학생 정보를 삭제합니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "삭제되었습니다.")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun deleteStudent(@Parameter(description = "삭제할 학생의 id", example = "1") @PathVariable studentId: Long): MessageResponse {
        deleteStudentUseCase.execute(studentId)
        return MessageResponse("삭제되었습니다.")
    }
}
