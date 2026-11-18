package app.xquare.xquareopenapi.domain.timetable.presentation.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

// 학년·반은 필수이고 범위는 학번 규칙과 같다(1~3학년, 1~9반). date를 생략하면 오늘(서울 시간)이 속한 주다.
data class TimetableSearchRequest(
    @field:NotNull
    @field:Min(1)
    @field:Max(3)
    val grade: Int? = null,

    @field:NotNull
    @field:Min(1)
    @field:Max(9)
    val classNum: Int? = null,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val date: LocalDate? = null,
)
