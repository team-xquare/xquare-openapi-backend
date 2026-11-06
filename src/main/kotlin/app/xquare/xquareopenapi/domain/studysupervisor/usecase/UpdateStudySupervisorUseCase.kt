package app.xquare.xquareopenapi.domain.studysupervisor.usecase

import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisor
import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisorAssignmentRepository
import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisorRepository
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateStudySupervisorUseCase(
    private val studySupervisorRepository: StudySupervisorRepository,
    private val assignmentRepository: StudySupervisorAssignmentRepository,
    private val studySupervisorAssigner: StudySupervisorAssigner,
) {

    // 등록처럼 모든 필드를 받아 통째로 바꾼다. supervisors는 전달한 목록으로 교체하고, 빠진 층은 배정이 없어진다.
    // 다른 정보가 있는 날짜로 옮기면 등록과 같은 409다. 날짜를 그대로 두는 수정은 된다.
    @Transactional
    fun execute(studySupervisorId: Long, request: StudySupervisorRequest) {
        val studySupervisor = studySupervisorRepository.findByIdForUpdate(studySupervisorId)
            ?: throw XquareException(ErrorCode.SUPERVISION_NOT_FOUND)
        val teachers = studySupervisorAssigner.loadTeachers(request.supervisors)
        if (studySupervisorRepository.existsByDateAndIdNot(request.date, studySupervisorId)) {
            throw XquareException(ErrorCode.SUPERVISION_ALREADY_EXISTS)
        }
        studySupervisor.moveTo(request.date)
        // 커밋할 때 반영하면 unique 제약 위반이 이 메서드 밖에서 나므로, 여기서 반영해 409로 바꾼다.
        try {
            studySupervisorRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            throw StudySupervisor.conflictOf(e)
        }
        assignmentRepository.deleteAllByStudySupervisorId(studySupervisorId)
        studySupervisorAssigner.assign(studySupervisor, request.supervisors, teachers)
    }
}
