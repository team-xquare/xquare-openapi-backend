package app.xquare.xquareopenapi.domain.student.usecase

import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteStudentUseCase(
    private val studentRepository: StudentRepository,
) {

    @Transactional
    fun execute(studentId: Long) {
        val student = studentRepository.findByIdOrNull(studentId) ?: throw XquareException(ErrorCode.STUDENT_NOT_FOUND)
        studentRepository.delete(student)
    }
}
