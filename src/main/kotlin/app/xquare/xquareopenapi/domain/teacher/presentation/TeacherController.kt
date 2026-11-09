package app.xquare.xquareopenapi.domain.teacher.presentation

import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherRequest
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherResponse
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherSearchRequest
import app.xquare.xquareopenapi.domain.teacher.usecase.CreateTeacherUseCase
import app.xquare.xquareopenapi.domain.teacher.usecase.DeleteTeacherUseCase
import app.xquare.xquareopenapi.domain.teacher.usecase.GetTeacherUseCase
import app.xquare.xquareopenapi.domain.teacher.usecase.GetTeachersUseCase
import app.xquare.xquareopenapi.domain.teacher.usecase.UpdateTeacherUseCase
import app.xquare.xquareopenapi.global.dto.MessageResponse
import app.xquare.xquareopenapi.global.dto.PageResponse
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
@RestController
@RequestMapping("/teachers")
class TeacherController(
    private val getTeachersUseCase: GetTeachersUseCase,
    private val createTeacherUseCase: CreateTeacherUseCase,
    private val getTeacherUseCase: GetTeacherUseCase,
    private val updateTeacherUseCase: UpdateTeacherUseCase,
    private val deleteTeacherUseCase: DeleteTeacherUseCase,
) {

    @GetMapping
    fun getTeachers(@ModelAttribute @Valid request: TeacherSearchRequest): PageResponse<TeacherResponse> =
        getTeachersUseCase.execute(request)

    @PostMapping
    fun createTeacher(@RequestBody @Valid request: TeacherRequest): ResponseEntity<MessageResponse> {
        val teacherId = createTeacherUseCase.execute(request)
        return ResponseEntity.created(URI.create("/teachers/$teacherId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/{teacherId}")
    fun getTeacher(@PathVariable teacherId: Long): TeacherResponse =
        getTeacherUseCase.execute(teacherId)

    @PatchMapping("/{teacherId}")
    fun updateTeacher(@PathVariable teacherId: Long, @RequestBody @Valid request: TeacherRequest): MessageResponse {
        updateTeacherUseCase.execute(teacherId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{teacherId}")
    fun deleteTeacher(@PathVariable teacherId: Long): MessageResponse {
        deleteTeacherUseCase.execute(teacherId)
        return MessageResponse("삭제되었습니다.")
    }
}
