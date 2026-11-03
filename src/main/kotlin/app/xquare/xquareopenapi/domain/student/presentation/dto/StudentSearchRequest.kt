package app.xquare.xquareopenapi.domain.student.presentation.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class StudentSearchRequest(
    val name: String? = null,

    // 학번이 1000~3999라 학년은 1~3, 반은 0~9만 나온다. 범위를 막지 않으면 큰 값에서 쿼리의 grade * 1000이 넘친다.
    @field:Min(1)
    @field:Max(3)
    val grade: Int? = null,

    @field:Min(0)
    @field:Max(9)
    val classNum: Int? = null,

    // page × size(최대 100)가 int를 넘으면 Spring Data가 offset을 만들지 못하고 500을 낸다.
    @field:Min(0)
    @field:Max(Int.MAX_VALUE / 100L)
    val page: Int = 0,

    @field:Min(1)
    @field:Max(100)
    val size: Int = 20,
)
