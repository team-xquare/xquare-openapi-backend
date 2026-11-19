package app.xquare.xquareopenapi.domain.studysupervisor.presentation

import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorDayRequest
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorDayResponse
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorRangeResponse
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorRequest
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorSearchRequest
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.CreateStudySupervisorUseCase
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.DeleteStudySupervisorUseCase
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.GetStudySupervisorByDayUseCase
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.GetStudySupervisorsUseCase
import app.xquare.xquareopenapi.domain.studysupervisor.usecase.UpdateStudySupervisorUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
import jakarta.validation.Valid
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

// 권한(조회는 USER, 등록·수정·삭제는 ADMIN)은 SecurityConfig에서 검사한다.
// 명세에 단건 조회가 없어서 GET /study-supervisors/{id}는 만들지 않았다. Location은 수정·삭제할 URI다.
@RestController
@RequestMapping("/study-supervisors")
class StudySupervisorController(
    private val getStudySupervisorsUseCase: GetStudySupervisorsUseCase,
    private val createStudySupervisorUseCase: CreateStudySupervisorUseCase,
    private val getStudySupervisorByDayUseCase: GetStudySupervisorByDayUseCase,
    private val updateStudySupervisorUseCase: UpdateStudySupervisorUseCase,
    private val deleteStudySupervisorUseCase: DeleteStudySupervisorUseCase,
) {

    @GetMapping
    fun getStudySupervisors(@ModelAttribute @Valid request: StudySupervisorSearchRequest): StudySupervisorRangeResponse =
        getStudySupervisorsUseCase.execute(request.startDate!!, request.finishDate!!)

    @PostMapping
    fun createStudySupervisor(@RequestBody @Valid request: StudySupervisorRequest): ResponseEntity<MessageResponse> {
        val studySupervisorId = createStudySupervisorUseCase.execute(request)
        return ResponseEntity.created(URI.create("/study-supervisors/$studySupervisorId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/day")
    fun getStudySupervisorByDay(@ModelAttribute @Valid request: StudySupervisorDayRequest): StudySupervisorDayResponse =
        getStudySupervisorByDayUseCase.execute(request.date!!)

    @PatchMapping("/{studySupervisorId}")
    fun updateStudySupervisor(
        @PathVariable studySupervisorId: Long,
        @RequestBody @Valid request: StudySupervisorRequest,
    ): MessageResponse {
        updateStudySupervisorUseCase.execute(studySupervisorId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{studySupervisorId}")
    fun deleteStudySupervisor(@PathVariable studySupervisorId: Long): MessageResponse {
        deleteStudySupervisorUseCase.execute(studySupervisorId)
        return MessageResponse("삭제되었습니다.")
    }
}
