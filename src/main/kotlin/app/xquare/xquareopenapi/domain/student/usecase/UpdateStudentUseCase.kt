package app.xquare.xquareopenapi.domain.student.usecase

import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateStudentUseCase(
    private val studentRepository: StudentRepository,
) {

    @Transactional
    fun execute(studentId: Long, request: StudentRequest) {
        val student = studentRepository.findByIdOrNull(studentId) ?: throw XquareException(ErrorCode.STUDENT_NOT_FOUND)
        if (request.studentNumber != student.studentNumber && studentRepository.existsByStudentNumber(request.studentNumber)) {
            throw XquareException(ErrorCode.STUDENT_NUMBER_ALREADY_EXISTS)
        }
        student.update(request.studentNumber, request.name, request.birthDate, request.email)
        // 커밋할 때 반영하면 unique 제약 위반이 이 메서드 밖에서 나므로, 여기서 반영해 409로 바꾼다.
        try {
            studentRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(Student.STUDENT_NUMBER_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.STUDENT_NUMBER_ALREADY_EXISTS)
            throw e
        }
    }
}
