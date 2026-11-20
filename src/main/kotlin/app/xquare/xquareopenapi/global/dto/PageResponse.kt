package app.xquare.xquareopenapi.global.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

@Schema(requiredProperties = ["content", "page", "size", "totalCount", "totalPage"])
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
