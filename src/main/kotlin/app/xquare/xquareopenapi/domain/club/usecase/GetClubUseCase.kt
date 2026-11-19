package app.xquare.xquareopenapi.domain.club.usecase

import app.xquare.xquareopenapi.domain.club.ClubRepository
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubResponse
import app.xquare.xquareopenapi.domain.student.StudentRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetClubUseCase(
    private val clubRepository: ClubRepository,
    private val studentRepository: StudentRepository,
) {

    @Transactional(readOnly = true)
    fun execute(clubId: Long): ClubResponse {
        val club = clubRepository.findWithManagerAndTeacherById(clubId) ?: throw XquareException(ErrorCode.CLUB_NOT_FOUND)
        return ClubResponse.of(club, studentRepository.findAllByClubIdIn(listOf(clubId)))
    }
}
