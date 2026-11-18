package app.xquare.xquareopenapi.domain.teacher.usecase

import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherResponse
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetTeacherUseCase(
    private val teacherRepository: TeacherRepository,
) {

    @Transactional(readOnly = true)
    fun execute(teacherId: Long): TeacherResponse {
        val teacher = teacherRepository.findByIdOrNull(teacherId) ?: throw XquareException(ErrorCode.TEACHER_NOT_FOUND)
        return TeacherResponse.from(teacher)
    }
}
