package app.xquare.xquareopenapi.domain.schedule.presentation

import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleRequest
import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleResponse
import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleSearchRequest
import app.xquare.xquareopenapi.domain.schedule.usecase.CreateScheduleUseCase
import app.xquare.xquareopenapi.domain.schedule.usecase.DeleteScheduleUseCase
import app.xquare.xquareopenapi.domain.schedule.usecase.GetScheduleUseCase
import app.xquare.xquareopenapi.domain.schedule.usecase.GetSchedulesUseCase
import app.xquare.xquareopenapi.domain.schedule.usecase.GetSchedulesUseCase.Companion.MAX_DAYS
import app.xquare.xquareopenapi.domain.schedule.usecase.GetSchedulesUseCase.Companion.MAX_RESULTS
import app.xquare.xquareopenapi.domain.schedule.usecase.UpdateScheduleUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.extensions.Extension
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

// 권한(조회는 로그인, 등록·수정·삭제는 ADMIN)은 SecurityConfig에서 검사한다.
@RestController
@RequestMapping("/schedules")
class ScheduleController(
    private val createScheduleUseCase: CreateScheduleUseCase,
    private val getScheduleUseCase: GetScheduleUseCase,
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val updateScheduleUseCase: UpdateScheduleUseCase,
    private val deleteScheduleUseCase: DeleteScheduleUseCase,
) {

    @PostMapping
    fun createSchedule(@RequestBody @Valid request: ScheduleRequest): ResponseEntity<MessageResponse> {
        val scheduleId = createScheduleUseCase.execute(request)
        return ResponseEntity.created(URI.create("/schedules/$scheduleId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listSchedules",
        tags = ["일정"],
        summary = "학사 일정 목록 조회",
        description = "시작일과 종료일을 포함해 최대 ${MAX_DAYS}일의 학사 일정을 날짜 오름차순, 같은 날짜는 id 오름차순으로 반환한다. " +
            "결과가 없으면 빈 배열이다. NEIS와 SCHOOL 일정을 모두 반환하며 ${MAX_RESULTS}건을 넘으면 " +
            "잘라서 반환하지 않고 더 짧은 기간을 요청하도록 400을 반환한다. 권한: 로그인 JWT(USER/ADMIN).",
        security = [SecurityRequirement(name = "bearerAuth")],
        extensions = [Extension(properties = [ExtensionProperty(name = "required-role", value = "USER")])],
    )
    @ApiResponse(
        responseCode = "200", description = "기간 내 학사 일정",
        content = [Content(array = ArraySchema(maxItems = MAX_RESULTS, schema = Schema(implementation = ScheduleResponse::class)))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_INPUT: 날짜 누락 또는 형식·범위 오류 / INVALID_DATE_RANGE: 종료일이 시작일보다 이르거나 " +
            "${MAX_DAYS}일 초과 / SCHEDULE_LIST_LIMIT_EXCEEDED: ${MAX_RESULTS}건 초과",
        content = [Content(schema = Schema(implementation = ScheduleListErrorSchema::class))],
    )
    @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: 유효한 액세스 토큰이 없음", content = [Content(schema = Schema(implementation = ScheduleListErrorSchema::class))])
    @ApiResponse(responseCode = "409", description = "CONCURRENT_UPDATE: 저장소 잠금 충돌", content = [Content(schema = Schema(implementation = ScheduleListErrorSchema::class))])
    @ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR: 저장소 접근 실패", content = [Content(schema = Schema(implementation = ScheduleListErrorSchema::class))])
    fun listSchedules(@ParameterObject @ModelAttribute @Valid query: ScheduleSearchRequest): List<ScheduleResponse> =
        getSchedulesUseCase.execute(query.startDate!!, query.finishDate!!)

    @GetMapping("/{scheduleId}")
    fun getSchedule(@PathVariable scheduleId: Long): ScheduleResponse =
        getScheduleUseCase.execute(scheduleId)

    @PatchMapping("/{scheduleId}")
    fun updateSchedule(@PathVariable scheduleId: Long, @RequestBody @Valid request: ScheduleRequest): MessageResponse {
        updateScheduleUseCase.execute(scheduleId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{scheduleId}")
    fun deleteSchedule(@PathVariable scheduleId: Long): MessageResponse {
        deleteScheduleUseCase.execute(scheduleId)
        return MessageResponse("삭제되었습니다.")
    }
}

@Schema(requiredProperties = ["code", "message"])
private data class ScheduleListErrorSchema(val code: String, val message: String)
