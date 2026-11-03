package app.xquare.xquareopenapi.domain.teacher.usecase

import app.xquare.xquareopenapi.domain.teacher.Teacher
import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateTeacherUseCase(
    private val teacherRepository: TeacherRepository,
) {

    // 등록한 교직원의 id를 돌려준다.
    @Transactional
    fun execute(request: TeacherRequest): Long {
        val teacher = teacherRepository.save(
            Teacher(
                name = request.name,
                grade = request.grade,
                classNum = request.classNum,
                subject = request.subject,
                office = request.office,
                isManager = request.isManager,
                position = request.position,
            ),
        )
        return teacher.id!!
    }
}
