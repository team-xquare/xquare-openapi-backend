package app.xquare.xquareopenapi.domain.student.presentation

import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentRequest
import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentResponse
import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentSearchRequest
import app.xquare.xquareopenapi.domain.student.usecase.CreateStudentUseCase
import app.xquare.xquareopenapi.domain.student.usecase.DeleteStudentUseCase
import app.xquare.xquareopenapi.domain.student.usecase.GetStudentUseCase
import app.xquare.xquareopenapi.domain.student.usecase.GetStudentsUseCase
import app.xquare.xquareopenapi.domain.student.usecase.UpdateStudentUseCase
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
@RequestMapping("/students")
class StudentController(
    private val getStudentsUseCase: GetStudentsUseCase,
    private val createStudentUseCase: CreateStudentUseCase,
    private val getStudentUseCase: GetStudentUseCase,
    private val updateStudentUseCase: UpdateStudentUseCase,
    private val deleteStudentUseCase: DeleteStudentUseCase,
) {

    @GetMapping
    fun getStudents(@ModelAttribute @Valid request: StudentSearchRequest): PageResponse<StudentResponse> =
        getStudentsUseCase.execute(request)

    @PostMapping
    fun createStudent(@RequestBody @Valid request: StudentRequest): ResponseEntity<MessageResponse> {
        val studentId = createStudentUseCase.execute(request)
        return ResponseEntity.created(URI.create("/students/$studentId")).body(MessageResponse("생성되었습니다."))
    }

    @GetMapping("/{studentId}")
    fun getStudent(@PathVariable studentId: Long): StudentResponse =
        getStudentUseCase.execute(studentId)

    @PatchMapping("/{studentId}")
    fun updateStudent(@PathVariable studentId: Long, @RequestBody @Valid request: StudentRequest): MessageResponse {
        updateStudentUseCase.execute(studentId, request)
        return MessageResponse("수정되었습니다.")
    }

    @DeleteMapping("/{studentId}")
    fun deleteStudent(@PathVariable studentId: Long): MessageResponse {
        deleteStudentUseCase.execute(studentId)
        return MessageResponse("삭제되었습니다.")
    }
}
