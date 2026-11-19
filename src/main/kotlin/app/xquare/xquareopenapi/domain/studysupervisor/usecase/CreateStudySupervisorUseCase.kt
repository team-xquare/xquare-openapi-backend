package app.xquare.xquareopenapi.domain.studysupervisor.usecase

import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisor
import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisorRepository
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateStudySupervisorUseCase(
    private val studySupervisorRepository: StudySupervisorRepository,
    private val studySupervisorAssigner: StudySupervisorAssigner,
) {

    // 등록한 자습 감독 정보의 id를 돌려준다. 날짜 하나에 정보 하나다.
    @Transactional
    fun execute(request: StudySupervisorRequest): Long {
        val teachers = studySupervisorAssigner.loadTeachers(request.supervisors)
        if (studySupervisorRepository.existsByDate(request.date)) throw XquareException(ErrorCode.SUPERVISION_ALREADY_EXISTS)
        val studySupervisor = StudySupervisor(request.date)
        try {
            studySupervisorRepository.saveAndFlush(studySupervisor)
        } catch (e: DataIntegrityViolationException) {
            throw StudySupervisor.conflictOf(e)
        }
        studySupervisorAssigner.assign(studySupervisor, request.supervisors, teachers)
        return studySupervisor.id!!
    }
}
