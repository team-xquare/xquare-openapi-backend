package app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto

import app.xquare.xquareopenapi.global.validation.DbDateRange
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

// 명세대로 둘 다 필수다. 빠지면 400 INVALID_INPUT. 기간 검사는 GetStudySupervisorsUseCase가 한다.
data class StudySupervisorSearchRequest(
    @field:Parameter(required = true, description = "조회 시작 날짜(yyyy-MM-dd, 1000-01-01~9999-12-31)", example = "2026-09-14")
    @field:NotNull
    @field:DbDateRange
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val startDate: LocalDate? = null,

    @field:Parameter(required = true, description = "조회 종료 날짜(yyyy-MM-dd, 1000-01-01~9999-12-31)", example = "2026-09-16")
    @field:NotNull
    @field:DbDateRange
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val finishDate: LocalDate? = null,
)

data class StudySupervisorDayRequest(
    @field:Parameter(required = true, description = "조회할 날짜(yyyy-MM-dd, 1000-01-01~9999-12-31)", example = "2026-09-14")
    @field:NotNull
    @field:DbDateRange
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val date: LocalDate? = null,
)
