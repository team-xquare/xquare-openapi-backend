package app.xquare.xquareopenapi.domain.studysupervisor.presentation.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate

// 등록과 수정이 같이 쓴다. 수정도 모든 필드를 받아 통째로 바꾸고, supervisors는 전달한 목록으로 교체한다.
// 일부 층만 보내도 된다. 빠진 층은 조회에서 teacher가 null이다. 같은 교직원이 여러 층을 맡는 것은 막지 않는다.
data class StudySupervisorRequest(
    val date: LocalDate,

    // [null]처럼 null이 섞이면 Jackson이 읽지 못해 400 INVALID_INPUT이 된다.
    @field:NotEmpty
    @field:Valid
    val supervisors: List<StudySupervisorAssignmentRequest>,
) {
    // 한 층에는 감독 교직원이 한 명이다(응답의 teacher가 객체 하나).
    @get:AssertTrue(message = "같은 층을 두 번 보낼 수 없습니다.")
    val isFloorsDistinct: Boolean
        get() = supervisors.distinctBy { it.floor }.size == supervisors.size
}

data class StudySupervisorAssignmentRequest(
    @field:Min(MIN_FLOOR)
    @field:Max(MAX_FLOOR)
    val floor: Int,

    val teacherId: Long,
) {
    companion object {
        // StudySupervisor.FLOORS와 같은 범위다. 어노테이션 값은 상수여야 해서 따로 둔다.
        const val MIN_FLOOR = 2L
        const val MAX_FLOOR = 4L
    }
}
