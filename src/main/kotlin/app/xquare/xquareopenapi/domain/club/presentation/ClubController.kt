package app.xquare.xquareopenapi.domain.club.presentation

import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubRequest
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubResponse
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubSearchRequest
import app.xquare.xquareopenapi.domain.club.usecase.CreateClubUseCase
import app.xquare.xquareopenapi.domain.club.usecase.DeleteClubUseCase
import app.xquare.xquareopenapi.domain.club.usecase.GetClubUseCase
import app.xquare.xquareopenapi.domain.club.usecase.GetClubsUseCase
import app.xquare.xquareopenapi.domain.club.usecase.UpdateClubUseCase
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
@Tag(name = "동아리")
@RestController
@RequestMapping("/clubs")
class ClubController(
    private val getClubsUseCase: GetClubsUseCase,
    private val createClubUseCase: CreateClubUseCase,
    private val getClubUseCase: GetClubUseCase,
    private val updateClubUseCase: UpdateClubUseCase,
    private val deleteClubUseCase: DeleteClubUseCase,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "searchClubs",
        summary = "동아리 검색",
        description = "동아리명(부분 일치), 구성원 이름(복수 AND, 부분 일치), 교실, 담당 선생님 이름(정확 일치)으로 동아리를 페이지 조회합니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "동아리 목록")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getClubs(@ParameterObject @ModelAttribute @Valid request: ClubSearchRequest): PageResponse<ClubResponse> =
        getClubsUseCase.execute(request)

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "createClub",
        summary = "동아리 등록",
        description = "동아리를 등록하고 Location 헤더로 상세조회 URI를 반환합니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(
        responseCode = "201",
        description = "생성됨",
        headers = [Header(name = "Location", description = "생성된 동아리를 상세조회하는 URI", schema = Schema(type = "string", format = "uri-reference", example = "/clubs/1"))],
        content = [Content(schema = Schema(implementation = MessageResponse::class))],
    )
    @ApiResponse(responseCode = "400", description = "ROOM_ALREADY_IN_USE / MANAGER_NOT_IN_MEMBER / INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND / TEACHER_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "STUDENT_ALREADY_IN_CLUB / TEACHER_ALREADY_IN_CLUB / CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun createClub(@RequestBody @Valid request: ClubRequest): ResponseEntity<MessageResponse> {
        val clubId = createClubUseCase.execute(request)
        return ResponseEntity.created(URI.create("/clubs/$clubId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/{clubId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getClub",
        summary = "동아리 조회",
        description = "동아리 1건을 id로 조회합니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "동아리 상세")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "CLUB_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getClub(@Parameter(description = "조회할 동아리의 id", example = "1") @PathVariable clubId: Long): ClubResponse =
        getClubUseCase.execute(clubId)

    @PatchMapping("/{clubId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "updateClub",
        summary = "동아리 수정",
        description = "동아리 정보를 수정합니다. 등록과 같은 필드를 모두 받아 통째로 바꿉니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "수정됨")
    @ApiResponse(responseCode = "400", description = "ROOM_ALREADY_IN_USE / MANAGER_NOT_IN_MEMBER / INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "CLUB_NOT_FOUND / STUDENT_NOT_FOUND / TEACHER_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "STUDENT_ALREADY_IN_CLUB / TEACHER_ALREADY_IN_CLUB / CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun updateClub(
        @Parameter(description = "수정할 동아리의 id", example = "1") @PathVariable clubId: Long,
        @RequestBody @Valid request: ClubRequest,
    ): MessageResponse {
        updateClubUseCase.execute(clubId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{clubId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "deleteClub",
        summary = "동아리 삭제",
        description = "동아리를 삭제합니다. 구성원 학생·담당 교직원은 지우지 않고 소속·담당 동아리만 null이 됩니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "삭제됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "CLUB_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun deleteClub(@Parameter(description = "삭제할 동아리의 id", example = "1") @PathVariable clubId: Long): MessageResponse {
        deleteClubUseCase.execute(clubId)
        return MessageResponse("삭제되었습니다.")
    }
}
