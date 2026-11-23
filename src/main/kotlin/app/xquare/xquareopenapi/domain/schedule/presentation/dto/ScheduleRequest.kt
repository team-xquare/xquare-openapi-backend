package app.xquare.xquareopenapi.domain.schedule.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

// 등록과 수정이 같이 쓴다. 수정도 모든 필드를 받아 통째로 바꾸므로 content를 생략하거나 null로 보내면 지운다.
data class ScheduleRequest(
    val date: LocalDate,

    @field:NotBlank
    @field:Size(max = MAX_TITLE_LENGTH)
    val title: String,

    @field:Size(max = MAX_CONTENT_LENGTH)
    val content: String? = null,
) {
    companion object {
        private const val MAX_TITLE_LENGTH = 50
        private const val MAX_CONTENT_LENGTH = 1000
    }
}
