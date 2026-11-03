package app.xquare.xquareopenapi.domain.student.usecase

import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentResponse
import app.xquare.xquareopenapi.domain.student.presentation.dto.StudentSearchRequest
import app.xquare.xquareopenapi.global.dto.PageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetStudentsUseCase(
    private val studentRepository: StudentRepository,
) {

    // 학번 순으로 정렬한다. 학번은 겹치지 않아서 페이지를 넘겨도 순서가 흔들리지 않는다.
    @Transactional(readOnly = true)
    fun execute(request: StudentSearchRequest): PageResponse<StudentResponse> {
        val pageable = PageRequest.of(request.page, request.size, Sort.by("studentNumber"))
        val students = studentRepository.search(request.name, request.grade, request.classNum, pageable)
        return PageResponse.of(students.map(StudentResponse::from))
    }
}
