package app.xquare.xquareopenapi.domain.timetable.presentation

import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableResponse
import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableSearchRequest
import app.xquare.xquareopenapi.domain.timetable.usecase.GetTimetableUseCase
import app.xquare.xquareopenapi.global.error.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.extensions.Extension
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 권한(USER)은 SecurityConfig의 기본 규칙(authenticated)으로 검사한다.
@Tag(name = "시간표")
@RestController
@RequestMapping("/timetables")
class TimetableController(
    private val getTimetableUseCase: GetTimetableUseCase,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "getTimetable",
        summary = "학급 주간 시간표 조회",
        description = "학급 하나의 한 주(월~일) 시간표를 나이스 고등학교시간표에서 가져와 반환합니다. date를 생략하면 오늘(서울 시간)이 속한 주를 조회합니다. 권한: USER.",
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "INVALID_INPUT", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    @ApiResponse(responseCode = "502", description = "TIMETABLE_UNAVAILABLE", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
    fun getTimetable(@ParameterObject @ModelAttribute @Valid request: TimetableSearchRequest): TimetableResponse =
        getTimetableUseCase.execute(request)
}
