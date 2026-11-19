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
        // 동아리 행을 잠갔으니 다른 요청이 이 동아리에 학생을 넣지 못한다. 기존 구성원 id를 읽어 새 구성원과 같이 잠근다.
        // 잠금 순서는 동아리 → 학생(id 순) → 담당 교직원이다. 동아리 API끼리는 이 순서가 같아 데드락이 나지 않는다.
        // 학생·교직원 삭제는 그 행을 먼저 지운 뒤 ON DELETE SET NULL로 동아리 행을 잠가 순서가 반대다. 그래서 이 수정과
        // 그 동아리의 팀장·구성원·담당 교직원 삭제가 겹치면 MySQL에서 데드락이 나고, DB가 되돌린 쪽(수정이든 삭제든)이
        // 409 CONCURRENT_UPDATE를 받는다(#26 리뷰, MySQL로 재현 안 함). 데이터는 안전하고 다시 보내면 된다.
        val relations = clubRelationLoader.load(request, clubId, studentRepository.findIdsByClubId(clubId))
        // 기존 구성원을 모두 빼고 새 목록을 넣는다. 계속 남는 학생은 값이 그대로라 UPDATE가 나가지 않는다.
        for (previous in relations.previousMembers) previous.leaveClub()
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
