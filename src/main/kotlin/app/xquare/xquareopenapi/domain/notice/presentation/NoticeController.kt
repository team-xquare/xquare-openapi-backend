package app.xquare.xquareopenapi.domain.notice.presentation

import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeDetailResponse
import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeListResponse
import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeRequest
import app.xquare.xquareopenapi.domain.notice.presentation.dto.NoticeSearchRequest
import app.xquare.xquareopenapi.domain.notice.usecase.CreateNoticeUseCase
import app.xquare.xquareopenapi.domain.notice.usecase.DeleteNoticeUseCase
import app.xquare.xquareopenapi.domain.notice.usecase.GetNoticeUseCase
import app.xquare.xquareopenapi.domain.notice.usecase.GetNoticesUseCase
import app.xquare.xquareopenapi.domain.notice.usecase.UpdateNoticeUseCase
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
import org.springframework.security.core.Authentication
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
@Tag(name = "공지")
@RestController
@RequestMapping("/notices")
class NoticeController(
    private val getNoticesUseCase: GetNoticesUseCase,
    private val createNoticeUseCase: CreateNoticeUseCase,
    private val getNoticeUseCase: GetNoticeUseCase,
    private val updateNoticeUseCase: UpdateNoticeUseCase,
    private val deleteNoticeUseCase: DeleteNoticeUseCase,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getNotices",
        summary = "공지 목록 조회",
        description = "공지 목록을 최신순(createdAt 내림차순, id 내림차순)으로 페이지 조회합니다. 본문은 포함하지 않습니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "공지 목록과 페이지 정보")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getNotices(@ParameterObject @ModelAttribute @Valid request: NoticeSearchRequest): NoticeListResponse =
        getNoticesUseCase.execute(request)

    // principal은 JwtAuthenticationFilter가 넣은 토큰의 사용자 id(Long)다. ADMIN만 들어오므로 항상 인증돼 있다.
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "createNotice",
        summary = "공지 등록",
        description = "공지를 등록합니다. 작성자는 토큰의 사용자로 설정됩니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(
        responseCode = "201",
        description = "생성됨",
        headers = [Header(name = "Location", description = "생성된 리소스를 상세조회하는 URI", schema = Schema(type = "string", format = "uri-reference", example = "/notices/1"))],
        content = [Content(schema = Schema(implementation = MessageResponse::class))],
    )
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun createNotice(authentication: Authentication, @RequestBody @Valid request: NoticeRequest): ResponseEntity<MessageResponse> {
        val noticeId = createNoticeUseCase.execute(authentication.principal as Long, request)
        return ResponseEntity.created(URI.create("/notices/$noticeId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/{noticeId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getNotice",
        summary = "공지 조회",
        description = "공지 1건을 id로 조회합니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "공지 상세")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getNotice(@Parameter(description = "조회할 공지의 id", example = "1") @PathVariable noticeId: Long): NoticeDetailResponse =
        getNoticeUseCase.execute(noticeId)

    @PatchMapping("/{noticeId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "updateNotice",
        summary = "공지 수정",
        description = "공지의 제목과 본문을 통째로 바꿉니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "수정됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "409", description = "CONCURRENT_UPDATE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun updateNotice(
        @Parameter(description = "수정할 공지의 id", example = "1") @PathVariable noticeId: Long,
        @RequestBody @Valid request: NoticeRequest,
    ): MessageResponse {
        updateNoticeUseCase.execute(noticeId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{noticeId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "deleteNotice",
        summary = "공지 삭제",
        description = "공지를 삭제합니다. 권한: ADMIN.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "ADMIN")])],
    )
    @ApiResponse(responseCode = "200", description = "삭제됨")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "403", description = "FORBIDDEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun deleteNotice(@Parameter(description = "삭제할 공지의 id", example = "1") @PathVariable noticeId: Long): MessageResponse {
        deleteNoticeUseCase.execute(noticeId)
        return MessageResponse("삭제되었습니다.")
    }
}
