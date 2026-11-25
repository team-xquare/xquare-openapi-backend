package app.xquare.xquareopenapi.domain.schedule.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

// 등록과 수정이 같이 쓴다. 수정도 모든 필드를 받아 통째로 바꾸므로 content를 생략하거나 null로 보내면 지운다.
@Schema(requiredProperties = ["date", "title"])
data class ScheduleRequest(
    @field:Schema(description = "일정 날짜", example = "2026-09-05")
    val date: LocalDate,

    @field:NotBlank
    @field:Size(max = MAX_TITLE_LENGTH)
    @field:Schema(description = "일정 제목(1~50자)", example = "체육대회")
    val title: String,

    @field:Size(max = MAX_CONTENT_LENGTH)
    @field:Schema(description = "일정 설명(생략·null 가능, 최대 1000자)", example = "우천 시 9월 12일로 순연")
    val content: String? = null,
) {
    companion object {
        private const val MAX_TITLE_LENGTH = 50
        private const val MAX_CONTENT_LENGTH = 1000
    }
}
