package app.xquare.xquareopenapi.domain.club.usecase

import app.xquare.xquareopenapi.domain.club.Club
import app.xquare.xquareopenapi.domain.club.ClubRepository
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubRequest
import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateClubUseCase(
    private val clubRepository: ClubRepository,
    private val studentRepository: StudentRepository,
    private val clubRelationLoader: ClubRelationLoader,
) {

    // 등록처럼 모든 필드를 받아 통째로 바꾼다. member는 전달한 목록으로 교체하고, 빠진 학생은 소속이 없어진다.
    @Transactional
    fun execute(clubId: Long, request: ClubRequest) {
        val club = clubRepository.findByIdForUpdate(clubId) ?: throw XquareException(ErrorCode.CLUB_NOT_FOUND)
        val relations = clubRelationLoader.load(request, clubId)
        // 기존 구성원을 모두 빼고 새 목록을 넣는다. 계속 남는 학생은 값이 그대로라 UPDATE가 나가지 않는다.
        for (previous in studentRepository.findAllByClubIdIn(listOf(clubId))) previous.leaveClub()
        for (member in relations.members) member.joinClub(club)
        club.update(request.name, request.logo, request.room, relations.manager, relations.teacher)
        // 커밋할 때 반영하면 unique 제약 위반이 이 메서드 밖에서 나므로, 여기서 반영해 에러 코드로 바꾼다.
        try {
            clubRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            throw Club.conflictOf(e)
        }
    }
}
