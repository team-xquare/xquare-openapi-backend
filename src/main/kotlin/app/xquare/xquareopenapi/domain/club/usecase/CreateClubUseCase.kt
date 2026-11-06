package app.xquare.xquareopenapi.domain.club.usecase

import app.xquare.xquareopenapi.domain.club.Club
import app.xquare.xquareopenapi.domain.club.ClubRepository
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateClubUseCase(
    private val clubRepository: ClubRepository,
    private val clubRelationLoader: ClubRelationLoader,
) {

    // 등록한 동아리의 id를 돌려준다.
    @Transactional
    fun execute(request: ClubRequest): Long {
        val relations = clubRelationLoader.load(request, null)
        val club = Club(request.name, request.logo, request.room, relations.manager, relations.teacher)
        try {
            clubRepository.saveAndFlush(club)
        } catch (e: DataIntegrityViolationException) {
            throw Club.conflictOf(e)
        }
        for (member in relations.members) member.joinClub(club)
        return club.id!!
    }
}
