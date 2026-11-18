package app.xquare.xquareopenapi.domain.student.usecase

import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetStudentUseCase(
    private val studentRepository: StudentRepository,
) {

    @Transactional(readOnly = true)
    fun execute(studentId: Long): StudentResponse {
        val student = studentRepository.findByIdOrNull(studentId) ?: throw XquareException(ErrorCode.STUDENT_NOT_FOUND)
        return StudentResponse.from(student)
    }
}
