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
        // MySQL(REPEATABLE READ)에서 이 delete는 uk(study_supervisor_id, floor_number) 인덱스에 gap lock을 잡는다.
        // 배정이 0건인 정보 두 개를 동시에 수정하면 두 트랜잭션이 같은 gap을 잡은 채 insert해 데드락이 나고, DB가 되돌린 쪽은
        // 409 CONCURRENT_UPDATE를 받는다(#28 리뷰, H2로는 재현되지 않고 MySQL로도 확인 안 함). 다시 보내면 성공하고 데이터는 섞이지 않는다.
        assignmentRepository.deleteAllByStudySupervisorId(studySupervisorId)
        studySupervisorAssigner.assign(studySupervisor, request.supervisors, teachers)
    }
}
