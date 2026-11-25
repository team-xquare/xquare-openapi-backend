package app.xquare.xquareopenapi.domain.notice.presentation.dto

import app.xquare.xquareopenapi.domain.notice.Notice
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 등록과 수정이 같이 쓴다. 수정도 두 필드를 모두 받아 통째로 바꾼다. 작성자는 토큰의 사용자라서 받지 않는다.
@Schema(requiredProperties = ["title", "content"])
data class NoticeRequest(
    @field:NotBlank
    @field:Size(max = Notice.TITLE_MAX_LENGTH)
    @field:Schema(description = "공지 제목(1~100자)", example = "2학기 동아리 모집 안내")
    val title: String,

    @field:NotBlank
    @field:Size(max = Notice.CONTENT_MAX_LENGTH)
    @field:Schema(description = "공지 본문(1~4000자)", example = "9월 15일까지 각 동아리 부장에게 신청서를 제출하세요.")
    val content: String,
)
