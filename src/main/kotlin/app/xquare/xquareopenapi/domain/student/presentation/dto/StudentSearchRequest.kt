package app.xquare.xquareopenapi.domain.student.presentation.dto

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class StudentSearchRequest(
    @field:Parameter(description = "검색할 학생 이름(부분 일치)", example = "민")
    val name: String? = null,

    // 학번이 1000~3999라 학년은 1~3, 반은 0~9만 나온다. 범위를 막지 않으면 큰 값에서 쿼리의 grade * 1000이 넘친다.
    @field:Parameter(description = "검색할 학년(1~3)", example = "2")
    @field:Min(1)
    @field:Max(3)
    val grade: Int? = null,

    @field:Parameter(description = "검색할 반(0~9)", example = "3")
    @field:Min(0)
    @field:Max(9)
    val classNum: Int? = null,

    // page × size(최대 100)가 int를 넘으면 Spring Data가 offset을 만들지 못하고 500을 낸다.
    @field:Parameter(description = "페이지 번호(0부터 시작)", example = "0")
    @field:Schema(defaultValue = "0")
    @field:Min(0)
    @field:Max(Int.MAX_VALUE / 100L)
    val page: Int = 0,

    @field:Parameter(description = "한 페이지에 담을 개수(1~100)", example = "20")
    @field:Schema(defaultValue = "20")
    @field:Min(1)
    @field:Max(100)
    val size: Int = 20,
)
