package app.xquare.xquareopenapi.domain.teacher.presentation.dto

import app.xquare.xquareopenapi.domain.teacher.TeacherSubject
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class TeacherSearchRequest(
    @field:Parameter(description = "교직원 이름(정확 일치)", example = "홍길동")
    val name: String? = null,
    @field:Parameter(description = "담당 학급 학년", example = "1")
    val grade: Int? = null,
    @field:Parameter(description = "담당 학급 반", example = "2")
    val classNum: Int? = null,
    @field:Parameter(description = "담당 과목")
    val subject: TeacherSubject? = null,
    @field:Parameter(description = "근무 교무실(부분 일치)", example = "3층 교무실")
    val office: String? = null,

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
