package app.xquare.xquareopenapi.domain.schedule.presentation.dto

import app.xquare.xquareopenapi.domain.schedule.Schedule
import app.xquare.xquareopenapi.domain.schedule.ScheduleSource
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(requiredProperties = ["id", "date", "title", "source"])
data class ScheduleResponse(
    val id: Long,
    val date: LocalDate,
    val title: String,
    @field:Schema(types = ["string", "null"])
    val content: String?,
    val source: ScheduleSource,
) {
    companion object {
        fun from(schedule: Schedule) = ScheduleResponse(
            id = schedule.id!!,
            date = schedule.date,
            title = schedule.title,
            content = schedule.content,
            source = schedule.source,
        )
    }
}
