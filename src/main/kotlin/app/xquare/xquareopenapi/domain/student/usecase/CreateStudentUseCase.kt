package app.xquare.xquareopenapi.domain.student.usecase

import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import app.xquare.xquareopenapi.global.error.violates
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateStudentUseCase(
    private val studentRepository: StudentRepository,
) {

    // 등록한 학생의 id를 돌려준다.
    @Transactional
    fun execute(request: StudentRequest): Long {
        if (studentRepository.existsByStudentNumber(request.studentNumber)) {
            throw XquareException(ErrorCode.STUDENT_NUMBER_ALREADY_EXISTS)
        }
        val student = Student(request.studentNumber, request.name, request.birthDate, request.email)
        // 위의 중복 검사와 저장 사이에 같은 학번이 먼저 등록되면 unique 제약에 걸린다. 이때도 409로 응답한다.
        try {
            studentRepository.saveAndFlush(student)
        } catch (e: DataIntegrityViolationException) {
            if (e.violates(Student.STUDENT_NUMBER_UNIQUE_CONSTRAINT)) throw XquareException(ErrorCode.STUDENT_NUMBER_ALREADY_EXISTS)
            throw e
        }
        return student.id!!
    }
}
