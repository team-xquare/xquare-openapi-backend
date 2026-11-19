package app.xquare.xquareopenapi.domain.notice.presentation.dto

import app.xquare.xquareopenapi.domain.notice.Notice
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 등록과 수정이 같이 쓴다. 수정도 두 필드를 모두 받아 통째로 바꾼다. 작성자는 토큰의 사용자라서 받지 않는다.
data class NoticeRequest(
    @field:NotBlank
    @field:Size(max = Notice.TITLE_MAX_LENGTH)
    val title: String,

    @field:NotBlank
    @field:Size(max = Notice.CONTENT_MAX_LENGTH)
    val content: String,
)
