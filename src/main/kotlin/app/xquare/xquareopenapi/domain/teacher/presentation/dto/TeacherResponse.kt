package app.xquare.xquareopenapi.domain.teacher.presentation.dto

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
) {
    // 동아리 기능이 아직 없어서 항상 null이다. 동아리를 만들 때 타입을 바꾼다.
    // getter가 있으면 "null 대신 null을 돌려주는" 죽일 수 없는 뮤턴트가 생겨서, getter 없이 필드로 직렬화한다.
    @JvmField
    val club: Nothing? = null

    companion object {
        fun from(teacher: Teacher) = TeacherResponse(
            id = teacher.id!!,
            name = teacher.name,
            grade = teacher.grade,
            classNum = teacher.classNum,
            subject = teacher.subject,
            office = teacher.office,
            isManager = teacher.isManager,
            position = teacher.position,
        )
    }
}
