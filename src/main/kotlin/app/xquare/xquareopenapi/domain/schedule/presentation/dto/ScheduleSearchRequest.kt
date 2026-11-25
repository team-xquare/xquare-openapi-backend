package app.xquare.xquareopenapi.domain.schedule.presentation.dto

import app.xquare.xquareopenapi.global.validation.DbDateRange
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

data class ScheduleSearchRequest(
    @field:Parameter(required = true, description = "조회 시작일 (yyyy-MM-dd, 1000-01-01~9999-12-31)")
    @field:NotNull
    @field:DbDateRange
    @DateTimeFormat(pattern = "uuuu-MM-dd")
    val startDate: LocalDate? = null,

    @field:Parameter(required = true, description = "조회 종료일 (yyyy-MM-dd, 1000-01-01~9999-12-31)")
    @field:NotNull
    @field:DbDateRange
    @DateTimeFormat(pattern = "uuuu-MM-dd")
    val finishDate: LocalDate? = null,
)