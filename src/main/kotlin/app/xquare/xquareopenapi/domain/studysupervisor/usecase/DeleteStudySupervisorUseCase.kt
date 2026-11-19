package app.xquare.xquareopenapi.domain.studysupervisor.usecase

import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisorRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteStudySupervisorUseCase(
    private val studySupervisorRepository: StudySupervisorRepository,
) {

    // 실제로 지운다. 층별 배정은 DB가 지우고, 교직원은 그대로 남는다.
    @Transactional
    fun execute(studySupervisorId: Long) {
        if (studySupervisorRepository.deleteByIdReturningCount(studySupervisorId) == 0) {
            throw XquareException(ErrorCode.SUPERVISION_NOT_FOUND)
        }
    }
}
