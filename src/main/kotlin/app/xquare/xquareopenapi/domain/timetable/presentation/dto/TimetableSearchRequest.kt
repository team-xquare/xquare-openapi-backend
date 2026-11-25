package app.xquare.xquareopenapi.domain.timetable.presentation.dto

import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

// 학년·반은 필수이고 범위는 학번 규칙과 같다(1~3학년, 1~9반). date를 생략하면 오늘(서울 시간)이 속한 주다.
data class TimetableSearchRequest(
    @field:Parameter(required = true, description = "학년(1~3)", example = "1")
    @field:NotNull
    @field:Min(1)
    @field:Max(3)
    val grade: Int? = null,

    @field:Parameter(required = true, description = "반(1~9)", example = "2")
    @field:NotNull
    @field:Min(1)
    @field:Max(9)
    val classNum: Int? = null,

    @field:Parameter(description = "조회할 주에 속한 날짜(yyyy-MM-dd). 생략하면 오늘(서울 시간)", example = "2026-11-18")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val date: LocalDate? = null,
)
