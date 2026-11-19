package app.xquare.xquareopenapi.domain.studysupervisor.usecase

import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisor
import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisorAssignment
import app.xquare.xquareopenapi.domain.studysupervisor.StudySupervisorAssignmentRepository
import app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto.StudySupervisorAssignmentRequest
import app.xquare.xquareopenapi.domain.teacher.Teacher
import app.xquare.xquareopenapi.domain.teacher.TeacherRepository
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.springframework.stereotype.Component

// 등록·수정이 같이 쓴다. 요청의 교직원을 읽고 층별 배정을 저장한다.
@Component
class StudySupervisorAssigner(
    private val teacherRepository: TeacherRepository,
    private val assignmentRepository: StudySupervisorAssignmentRepository,
) {

    // 없는 교직원이 하나라도 있으면 TEACHER_NOT_FOUND. 같은 교직원이 여러 층을 맡으면 한 번만 읽는다.
    fun loadTeachers(assignments: List<StudySupervisorAssignmentRequest>): Map<Long, Teacher> {
        val teacherIds = assignments.map { it.teacherId }.toSet()
        val teachers = teacherRepository.findAllById(teacherIds).associateBy { it.id!! }
        if (teachers.size != teacherIds.size) throw XquareException(ErrorCode.TEACHER_NOT_FOUND)
        return teachers
    }

    fun assign(studySupervisor: StudySupervisor, assignments: List<StudySupervisorAssignmentRequest>, teachers: Map<Long, Teacher>) {
        assignmentRepository.saveAll(
            assignments.map { StudySupervisorAssignment(studySupervisor, it.floor, teachers.getValue(it.teacherId)) },
        )
    }
}
