package app.xquare.xquareopenapi.domain.timetable.presentation

import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableResponse
import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableSearchRequest
import app.xquare.xquareopenapi.domain.timetable.usecase.GetTimetableUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 권한(USER)은 SecurityConfig의 기본 규칙(authenticated)으로 검사한다.
@RestController
@RequestMapping("/timetables")
class TimetableController(
    private val getTimetableUseCase: GetTimetableUseCase,
) {

    @GetMapping
    fun getTimetable(@ModelAttribute @Valid request: TimetableSearchRequest): TimetableResponse =
        getTimetableUseCase.execute(request)
}
