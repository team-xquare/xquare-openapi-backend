package app.xquare.xquareopenapi.domain.timetable.presentation.dto

import app.xquare.xquareopenapi.domain.timetable.neis.NeisTimetable
import java.time.LocalDate

// 수업이 없는 날(주말·방학·공휴일)은 days에 넣지 않는다.
data class TimetableResponse(
    val grade: Int,
    val classNum: Int,
    val startDate: LocalDate,
    val finishDate: LocalDate,
    val days: List<TimetableDayResponse>,
) {
    companion object {
        // timetables는 날짜·교시 순이어야 한다(NeisTimetableClient가 정렬해 준다).
        fun of(grade: Int, classNum: Int, startDate: LocalDate, finishDate: LocalDate, timetables: List<NeisTimetable>) =
            TimetableResponse(
                grade = grade,
                classNum = classNum,
                startDate = startDate,
                finishDate = finishDate,
                days = timetables.groupBy(NeisTimetable::date).map { (date, periods) ->
                    TimetableDayResponse(date, periods.map { TimetablePeriodResponse(it.period, it.subject) })
                },
            )
    }
}

data class TimetableDayResponse(
    val date: LocalDate,
    val periods: List<TimetablePeriodResponse>,
)

data class TimetablePeriodResponse(
    val period: Int,
    val subject: String,
)
