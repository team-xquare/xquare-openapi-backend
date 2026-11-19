package app.xquare.xquareopenapi.domain.teacher.usecase

import app.xquare.xquareopenapi.domain.club.ClubRepository
import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherResponse
import app.xquare.xquareopenapi.domain.teacher.presentation.dto.TeacherSearchRequest
import app.xquare.xquareopenapi.global.dto.PageResponse
import app.xquare.xquareopenapi.global.jpa.escapeLike
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetTeachersUseCase(
    private val teacherRepository: TeacherRepository,
    private val clubRepository: ClubRepository,
) {

    // 등록한 순서(id)로 정렬한다. 담당 동아리는 페이지의 교직원을 모아 한 번에 읽는다.
    @Transactional(readOnly = true)
    fun execute(request: TeacherSearchRequest): PageResponse<TeacherResponse> {
        val pageable = PageRequest.of(request.page, request.size, Sort.by("id"))
        val teachers = teacherRepository.search(
            request.name,
            request.grade,
            request.classNum,
            request.subject,
            request.office?.escapeLike(),
            pageable,
        )
        val clubByTeacherId = clubRepository.findAllByTeacherIdIn(teachers.content.map { it.id!! }).associateBy { it.teacher!!.id!! }
        return PageResponse.of(teachers.map { TeacherResponse.from(it, clubByTeacherId[it.id]) })
    }
}
