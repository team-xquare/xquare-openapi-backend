package app.xquare.xquareopenapi.domain.student.presentation.dto

import app.xquare.xquareopenapi.domain.club.presentation.dto.ClubSummaryResponse
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
    // 소속 동아리가 없으면 null이다.
    val club: ClubSummaryResponse?,
) {
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
            club = ClubSummaryResponse.from(student.club),
        )
    }
}
