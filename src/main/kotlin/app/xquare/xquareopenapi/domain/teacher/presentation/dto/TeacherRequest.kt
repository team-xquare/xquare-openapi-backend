package app.xquare.xquareopenapi.domain.teacher.presentation.dto

import app.xquare.xquareopenapi.domain.teacher.TeacherPosition
import app.xquare.xquareopenapi.domain.teacher.TeacherSubject
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 등록과 수정이 같이 쓴다. 수정도 모든 필드를 받아 통째로 바꾸므로, grade와 classNum을 null로 보내면 담임에서 빠진다.
// 학년·반 범위는 학번 규칙(1000~3999, 반은 한 자리)에 맞춘다.
data class TeacherRequest(
    @field:NotBlank
    @field:Size(max = 10)
    val name: String,

    @field:Min(1)
    @field:Max(3)
    val grade: Int?,

    @field:Min(1)
    @field:Max(9)
    val classNum: Int?,

    val subject: TeacherSubject,

    @field:NotBlank
    @field:Size(max = 30)
    val office: String,

    val isManager: Boolean,

    val position: TeacherPosition,
) {
    @get:AssertTrue(message = "grade와 classNum은 함께 보내거나 함께 비워야 합니다.")
    val isHomeroomComplete: Boolean
        get() = (grade == null) == (classNum == null)
}
