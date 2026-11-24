package app.xquare.xquareopenapi.domain.timetable.presentation

import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableResponse
import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableSearchRequest
import app.xquare.xquareopenapi.domain.timetable.usecase.GetTimetableUseCase
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 공개(PUBLIC) 단계라 SecurityConfig가 키·토큰 없이 연다. 문서에서도 기본 인증(Bearer)을 빈 배열로 재정의한다.
@RestController
@RequestMapping("/timetables")
class TimetableController(
    private val getTimetableUseCase: GetTimetableUseCase,
) {

    @SecurityRequirements
    @GetMapping
    fun getTimetable(@ModelAttribute @Valid request: TimetableSearchRequest): TimetableResponse =
        getTimetableUseCase.execute(request)
}
