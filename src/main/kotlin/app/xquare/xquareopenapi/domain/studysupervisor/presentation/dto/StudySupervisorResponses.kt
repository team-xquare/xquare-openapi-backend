package app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto

import app.xquare.xquareopenapi.domain.teacher.Teacher
import java.time.LocalDate

// id는 명세에 없지만 넣었다. 단건 조회가 없어서 이게 없으면 등록 때의 Location 말고는 수정·삭제할 id를 알 수 없다.
// 정보가 없는 날짜는 id가 null이고 supervisors가 빈 배열이다.
data class StudySupervisorDayResponse(
    val id: Long?,
    val date: LocalDate,
    val supervisors: List<StudySupervisorFloorResponse>,
)

// 배정되지 않은 층은 teacher가 null이다.
data class StudySupervisorFloorResponse(
    val floor: Int,
    val teacher: StudySupervisorTeacherResponse?,
)

data class StudySupervisorTeacherResponse(
    val teacherId: Long,
    val name: String,
) {
    companion object {
        fun from(teacher: Teacher) = StudySupervisorTeacherResponse(teacher.id!!, teacher.name)
    }
}

data class StudySupervisorRangeResponse(
    val startDate: LocalDate,
    val finishDate: LocalDate,
    val supervisions: List<StudySupervisorDayResponse>,
)
