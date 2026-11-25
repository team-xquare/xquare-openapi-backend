package app.xquare.xquareopenapi.domain.notice.presentation.dto

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class NoticeSearchRequest(
    // page × size(최대 100)가 int를 넘으면 Spring Data가 offset을 만들지 못하고 500을 낸다.
    @field:Parameter(description = "페이지 번호(0부터 시작)", example = "0")
    @field:Schema(defaultValue = "0")
    @field:Min(0)
    @field:Max(Int.MAX_VALUE / 100L)
    val page: Int = 0,

    @field:Parameter(description = "페이지당 개수(1~100)", example = "20")
    @field:Schema(defaultValue = "20")
    @field:Min(1)
    @field:Max(100)
    val size: Int = 20,
)
