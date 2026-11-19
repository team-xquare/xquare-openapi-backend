package app.xquare.xquareopenapi.domain.club.usecase

import app.xquare.xquareopenapi.domain.club.ClubRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteClubUseCase(
    private val clubRepository: ClubRepository,
) {

    // 학생·교직원은 지우지 않는다. 구성원의 소속은 DB가 null로 바꾼다.
    @Transactional
    fun execute(clubId: Long) {
        if (clubRepository.deleteByIdReturningCount(clubId) == 0) {
            throw XquareException(ErrorCode.CLUB_NOT_FOUND)
        }
    }
}
