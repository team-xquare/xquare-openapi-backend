package app.xquare.xquareopenapi.domain.club.usecase

import app.xquare.xquareopenapi.domain.club.ClubRepository
import app.xquare.xquareopenapi.domain.club.ClubSearchSpecification
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubResponse
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubSearchRequest
import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.global.dto.PageResponse
import app.xquare.xquareopenapi.global.jpa.escapeLike
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetClubsUseCase(
    private val clubRepository: ClubRepository,
    private val studentRepository: StudentRepository,
) {

    // 등록한 순서(id)로 정렬한다. 구성원은 페이지의 동아리를 모아 한 번에 읽는다.
    @Transactional(readOnly = true)
    fun execute(request: ClubSearchRequest): PageResponse<ClubResponse> {
        val pageable = PageRequest.of(request.page, request.size, Sort.by("id"))
        val spec = ClubSearchSpecification.of(
            request.name?.escapeLike(),
            request.member.map { it.escapeLike() },
            request.room,
            request.teacher,
        )
        val clubs = clubRepository.findAll(spec, pageable)
        val membersByClubId = studentRepository.findAllByClubIdIn(clubs.content.map { it.id!! }).groupBy { it.club!!.id!! }
        return PageResponse.of(clubs.map { ClubResponse.of(it, membersByClubId[it.id].orEmpty()) })
    }
}
