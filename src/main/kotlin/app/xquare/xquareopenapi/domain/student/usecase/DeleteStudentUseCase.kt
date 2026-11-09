package app.xquare.xquareopenapi.domain.student.usecase

import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteStudentUseCase(
    private val studentRepository: StudentRepository,
) {

    @Transactional
    fun execute(studentId: Long) {
        if (studentRepository.deleteByIdReturningCount(studentId) == 0) {
            throw XquareException(ErrorCode.STUDENT_NOT_FOUND)
        }
    }
}
