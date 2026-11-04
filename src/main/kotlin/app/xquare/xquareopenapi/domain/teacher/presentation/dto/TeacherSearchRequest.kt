package app.xquare.xquareopenapi.domain.teacher.presentation.dto

import app.xquare.xquareopenapi.domain.teacher.TeacherSubject
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class TeacherSearchRequest(
    val name: String? = null,
    val grade: Int? = null,
    val classNum: Int? = null,
    val subject: TeacherSubject? = null,
    val office: String? = null,

    // page × size(최대 100)가 int를 넘으면 Spring Data가 offset을 만들지 못하고 500을 낸다.
    @field:Min(0)
    @field:Max(Int.MAX_VALUE / 100L)
    val page: Int = 0,

    @field:Min(1)
    @field:Max(100)
    val size: Int = 20,
)
