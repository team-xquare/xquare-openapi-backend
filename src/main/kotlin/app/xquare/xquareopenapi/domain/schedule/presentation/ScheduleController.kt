package app.xquare.xquareopenapi.domain.schedule.presentation

import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleRequest
import app.xquare.xquareopenapi.domain.schedule.presentation.dto.ScheduleResponse
import app.xquare.xquareopenapi.domain.schedule.usecase.CreateScheduleUseCase
import app.xquare.xquareopenapi.domain.schedule.usecase.DeleteScheduleUseCase
import app.xquare.xquareopenapi.domain.schedule.usecase.GetScheduleUseCase
import app.xquare.xquareopenapi.domain.schedule.usecase.UpdateScheduleUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

// 권한(조회는 USER, 등록·수정·삭제는 ADMIN)은 SecurityConfig에서 검사한다.
@RestController
@RequestMapping("/schedules")
class ScheduleController(
    private val createScheduleUseCase: CreateScheduleUseCase,
    private val getScheduleUseCase: GetScheduleUseCase,
    private val updateScheduleUseCase: UpdateScheduleUseCase,
    private val deleteScheduleUseCase: DeleteScheduleUseCase,
) {

    @PostMapping
    fun createSchedule(@RequestBody @Valid request: ScheduleRequest): ResponseEntity<MessageResponse> {
        val scheduleId = createScheduleUseCase.execute(request)
        return ResponseEntity.created(URI.create("/schedules/$scheduleId")).body(MessageResponse("생성되었습니다."))
    }

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
