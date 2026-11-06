package app.xquare.xquareopenapi.domain.teacher.presentation.dto

import app.xquare.xquareopenapi.domain.club.Club
import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubSummaryResponse
import app.xquare.xquareopenapi.domain.teacher.Teacher
import app.xquare.xquareopenapi.domain.teacher.TeacherPosition
import app.xquare.xquareopenapi.domain.teacher.TeacherSubject

data class TeacherResponse(
    val id: Long,
    val name: String,
    val grade: Int?,
    val classNum: Int?,
    val subject: TeacherSubject,
    val office: String,
    val isManager: Boolean,
    val position: TeacherPosition,
    // 담당 동아리가 없으면 null이다.
    val club: ClubSummaryResponse?,
) {
    companion object {
        // club은 이 교직원이 담당하는 동아리다. 동아리 쪽(clubs.teacher_id)에 저장돼 있어서 호출하는 쪽이 찾아 넘긴다.
        fun from(teacher: Teacher, club: Club?) = TeacherResponse(
            id = teacher.id!!,
            name = teacher.name,
            grade = teacher.grade,
            classNum = teacher.classNum,
            subject = teacher.subject,
            office = teacher.office,
            isManager = teacher.isManager,
            position = teacher.position,
            club = ClubSummaryResponse.from(club),
        )
    }
}
