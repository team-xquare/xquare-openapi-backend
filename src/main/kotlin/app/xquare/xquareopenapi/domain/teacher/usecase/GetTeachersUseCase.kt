package app.xquare.xquareopenapi.domain.teacher.usecase

import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherResponse
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherSearchRequest
import app.xquare.xquareopenapi.global.dto.PageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetTeachersUseCase(
    private val teacherRepository: TeacherRepository,
) {

    // 등록한 순서(id)로 정렬한다.
    @Transactional(readOnly = true)
    fun execute(request: TeacherSearchRequest): PageResponse<TeacherResponse> {
        val pageable = PageRequest.of(request.page, request.size, Sort.by("id"))
        val teachers = teacherRepository.search(
            request.name,
            request.grade,
            request.classNum,
            request.subject,
            request.office,
            pageable,
        )
        return PageResponse.of(teachers.map(TeacherResponse::from))
    }
}
