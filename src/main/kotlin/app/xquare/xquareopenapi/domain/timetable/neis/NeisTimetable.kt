package app.xquare.xquareopenapi.domain.timetable.neis

import java.time.LocalDate

// 나이스 고등학교시간표(hisTimetable)의 행 하나. subject는 수업내용(ITRT_CNTNT)이다.
data class NeisTimetable(
    val date: LocalDate,
    val period: Int,
    val subject: String,
)
