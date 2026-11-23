package app.xquare.xquareopenapi.domain.schedule.neis

import java.time.LocalDate

// 나이스 학사일정(SchoolSchedule)의 행 하나. 내용(EVENT_CNTNT)이 비어 있으면 null이다.
data class NeisSchedule(
    val date: LocalDate,
    val title: String,
    val content: String?,
)
