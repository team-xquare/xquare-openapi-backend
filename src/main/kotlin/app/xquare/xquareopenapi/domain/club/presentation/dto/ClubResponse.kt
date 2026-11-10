package app.xquare.xquareopenapi.domain.club.presentation.dto

import app.xquare.xquareopenapi.domain.club.Club
import app.xquare.xquareopenapi.domain.club.ClubRoom
import app.xquare.xquareopenapi.domain.student.Student
import app.xquare.xquareopenapi.domain.teacher.Teacher

data class ClubResponse(
    val id: Long,
    val name: String,
    val logo: String,
    val room: ClubRoom,
    // 팀장 학생이 삭제되면 새 팀장을 정할 때까지 null이다.
    val manager: ClubStudentResponse?,
    val member: List<ClubStudentResponse>,
    val teacher: ClubTeacherResponse?,
) {
    companion object {
        // members는 이 동아리 구성원을 학번 순으로 정렬한 목록이다.
        fun of(club: Club, members: List<Student>): ClubResponse {
            val manager = club.manager
            val teacher = club.teacher
            return ClubResponse(
                id = club.id!!,
                name = club.name,
                logo = club.logo,
                room = club.room,
                manager = if (manager == null) null else ClubStudentResponse.from(manager),
                member = members.map(ClubStudentResponse::from),
                teacher = if (teacher == null) null else ClubTeacherResponse.from(teacher),
            )
        }
    }
}

data class ClubStudentResponse(
    val id: Long,
    val studentNumber: Int,
    val name: String,
) {
    companion object {
        fun from(student: Student) = ClubStudentResponse(student.id!!, student.studentNumber, student.name)
    }
}

data class ClubTeacherResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(teacher: Teacher) = ClubTeacherResponse(teacher.id!!, teacher.name)
    }
}
