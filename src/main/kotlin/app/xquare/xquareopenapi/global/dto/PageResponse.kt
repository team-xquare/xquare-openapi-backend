package app.xquare.xquareopenapi.global.dto

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
    val totalPage: Int,
) {
    companion object {
        fun <T : Any> of(page: Page<T>) = PageResponse(page.content, page.number, page.size, page.totalElements, page.totalPages)
    }
}
