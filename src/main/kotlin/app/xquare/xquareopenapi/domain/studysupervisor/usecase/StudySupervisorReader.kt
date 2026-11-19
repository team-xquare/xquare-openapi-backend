package app.xquare.xquareopenapi.domain.studysupervisor.usecase

import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisor
import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisorAssignmentRepository
import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisorRepository
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorDayResponse
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorFloorResponse
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorTeacherResponse
import org.springframework.stereotype.Component
import java.time.LocalDate

// 기간 조회와 일별 조회가 같이 쓴다. 기간 안의 모든 날짜를 날짜 순으로 준다.
// 정보가 없는 날짜는 빈 배열, 정보가 있는 날짜는 모든 층(StudySupervisor.FLOORS)을 주고 배정이 없는 층은 teacher가 null이다.
@Component
class StudySupervisorReader(
    private val studySupervisorRepository: StudySupervisorRepository,
    private val assignmentRepository: StudySupervisorAssignmentRepository,
) {

    // 날짜 수와 상관없이 쿼리 두 번으로 읽는다.
    fun read(startDate: LocalDate, finishDate: LocalDate): List<StudySupervisorDayResponse> {
        val supervisorsByDate = studySupervisorRepository.findAllByDateBetween(startDate, finishDate).associateBy { it.date }
        val assignmentsBySupervisor = assignmentRepository
            .findAllWithTeacherByStudySupervisorIdIn(supervisorsByDate.values.map { it.id!! })
            .groupBy { it.studySupervisor.id!! }
        return startDate.datesUntil(finishDate.plusDays(1)).toList().map { date ->
            val supervisor = supervisorsByDate[date]
            if (supervisor == null) {
                StudySupervisorDayResponse(null, date, emptyList())
            } else {
                val teachersByFloor = assignmentsBySupervisor[supervisor.id!!].orEmpty().associate { it.floor to it.teacher }
                val floors = StudySupervisor.FLOORS.map { floor ->
                    StudySupervisorFloorResponse(floor, teachersByFloor[floor]?.let(StudySupervisorTeacherResponse::from))
                }
                StudySupervisorDayResponse(supervisor.id, date, floors)
            }
        }
    }
}
