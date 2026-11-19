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
import jakarta.validation.Valid
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
@RestController
@RequestMapping("/notices")
class NoticeController(
    private val getNoticesUseCase: GetNoticesUseCase,
    private val createNoticeUseCase: CreateNoticeUseCase,
    private val getNoticeUseCase: GetNoticeUseCase,
    private val updateNoticeUseCase: UpdateNoticeUseCase,
    private val deleteNoticeUseCase: DeleteNoticeUseCase,
) {

    @GetMapping
    fun getNotices(@ModelAttribute @Valid request: NoticeSearchRequest): NoticeListResponse =
        getNoticesUseCase.execute(request)

    // principal은 JwtAuthenticationFilter가 넣은 토큰의 사용자 id(Long)다. ADMIN만 들어오므로 항상 인증돼 있다.
    @PostMapping
    fun createNotice(authentication: Authentication, @RequestBody @Valid request: NoticeRequest): ResponseEntity<MessageResponse> {
        val noticeId = createNoticeUseCase.execute(authentication.principal as Long, request)
        return ResponseEntity.created(URI.create("/notices/$noticeId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/{noticeId}")
    fun getNotice(@PathVariable noticeId: Long): NoticeDetailResponse =
        getNoticeUseCase.execute(noticeId)

    @PatchMapping("/{noticeId}")
    fun updateNotice(@PathVariable noticeId: Long, @RequestBody @Valid request: NoticeRequest): MessageResponse {
        updateNoticeUseCase.execute(noticeId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{noticeId}")
    fun deleteNotice(@PathVariable noticeId: Long): MessageResponse {
        deleteNoticeUseCase.execute(noticeId)
        return MessageResponse("삭제되었습니다.")
    }
}
