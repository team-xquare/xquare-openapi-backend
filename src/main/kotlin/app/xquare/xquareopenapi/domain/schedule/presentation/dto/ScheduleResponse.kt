package app.xquare.xquareopenapi.domain.schedule.presentation.dto

import app.xquare.xquareopenapi.domain.schedule.Schedule
import app.xquare.xquareopenapi.domain.schedule.ScheduleSource
import java.time.LocalDate

data class ScheduleResponse(
    val id: Long,
    val date: LocalDate,
    val title: String,
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
