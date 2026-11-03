package app.xquare.xquareopenapi.domain.teacher.usecase

import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateTeacherUseCase(
    private val teacherRepository: TeacherRepository,
) {

    @Transactional
    fun execute(teacherId: Long, request: TeacherRequest) {
        val teacher = teacherRepository.findByIdOrNull(teacherId) ?: throw XquareException(ErrorCode.TEACHER_NOT_FOUND)
        teacher.update(
            name = request.name,
            grade = request.grade,
            classNum = request.classNum,
            subject = request.subject,
            office = request.office,
            isManager = request.isManager,
            position = request.position,
        )
    }
}
