package app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto

import app.xquare.xquareopenapi.global.validation.DbDateRange
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

// 명세대로 둘 다 필수다. 빠지면 400 INVALID_INPUT. 기간 검사는 GetStudySupervisorsUseCase가 한다.
data class StudySupervisorSearchRequest(
    @field:NotNull
    @field:DbDateRange
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val startDate: LocalDate? = null,

    @field:NotNull
    @field:DbDateRange
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val finishDate: LocalDate? = null,
)

data class StudySupervisorDayRequest(
    @field:NotNull
    @field:DbDateRange
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val date: LocalDate? = null,
)
