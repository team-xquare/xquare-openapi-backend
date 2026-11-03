package app.xquare.xquareopenapi.domain.student.presentation.dto

import app.xquare.xquareopenapi.domain.student.Student
import java.time.LocalDate

data class StudentResponse(
    val id: Long,
    val studentNumber: Int,
    val grade: Int,
    val classNum: Int,
    val number: Int,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
) {
    // 동아리 기능이 아직 없어서 항상 null이다. 동아리를 만들 때 타입을 바꾼다.
    val club: Nothing? = null

    companion object {
        fun from(student: Student) = StudentResponse(
            id = student.id!!,
            studentNumber = student.studentNumber,
            grade = student.studentNumber / 1000,
            classNum = student.studentNumber / 100 % 10,
            number = student.studentNumber % 100,
            name = student.name,
            birthDate = student.birthDate,
            email = student.email,
        )
    }
}
