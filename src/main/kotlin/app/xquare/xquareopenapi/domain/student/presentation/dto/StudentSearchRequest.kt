package app.xquare.xquareopenapi.domain.student.presentation.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class StudentSearchRequest(
    val name: String? = null,
    val grade: Int? = null,
    val classNum: Int? = null,

    @field:Min(0)
    val page: Int = 0,

    @field:Min(1)
    @field:Max(100)
    val size: Int = 20,
)
