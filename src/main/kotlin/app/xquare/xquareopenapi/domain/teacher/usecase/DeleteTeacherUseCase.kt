package app.xquare.xquareopenapi.domain.teacher.usecase

import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteTeacherUseCase(
    private val teacherRepository: TeacherRepository,
) {

    @Transactional
    fun execute(teacherId: Long) {
        if (teacherRepository.deleteByIdReturningCount(teacherId) == 0) {
            throw XquareException(ErrorCode.TEACHER_NOT_FOUND)
        }
    }
}
