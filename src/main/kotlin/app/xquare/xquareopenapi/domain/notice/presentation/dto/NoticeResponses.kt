package app.xquare.xquareopenapi.domain.notice.presentation.dto

import app.xquare.xquareopenapi.domain.notice.Notice
import org.springframework.data.domain.Page
import java.time.LocalDateTime

// 명세의 목록 필드 이름이 content가 아니라 notices라서 PageResponse와 같은 모양으로 따로 둔다.
data class NoticeListResponse(
    val notices: List<NoticeSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
    val totalPage: Int,
) {
    companion object {
        fun of(page: Page<NoticeSummaryResponse>) =
            NoticeListResponse(page.content, page.number, page.size, page.totalElements, page.totalPages)
    }
}

// 목록에는 본문을 넣지 않는다.
data class NoticeSummaryResponse(
    val id: Long,
    val title: String,
    // 작성자 사용자가 지워졌으면 null이다.
    val author: String?,
    val createdAt: LocalDateTime,
    // 수정된 적이 없으면 null이다.
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(notice: Notice) = NoticeSummaryResponse(
            id = notice.id!!,
            title = notice.title,
            author = notice.author?.name,
            createdAt = notice.createdAt,
            updatedAt = notice.updatedAt,
        )
    }
}

data class NoticeDetailResponse(
    val id: Long,
    val title: String,
    val content: String,
    val author: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(notice: Notice) = NoticeDetailResponse(
            id = notice.id!!,
            title = notice.title,
            content = notice.content,
            author = notice.author?.name,
            createdAt = notice.createdAt,
            updatedAt = notice.updatedAt,
        )
    }
}
