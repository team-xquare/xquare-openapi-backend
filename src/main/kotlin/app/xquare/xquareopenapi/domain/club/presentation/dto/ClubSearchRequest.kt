package app.xquare.xquareopenapi.domain.club.presentation.dto

import app.xquare.xquareopenapi.domain.club.ClubRoom
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class ClubSearchRequest(
    @field:Parameter(description = "검색할 동아리명(부분 일치)", example = "DMS")
    val name: String? = null,

    // member=성준&member=민수처럼 여러 번 줄 수 있다. 이름마다 조건이 하나씩 늘어나서 개수를 막는다.
    @field:Parameter(description = "동아리 구성원 이름(부분 일치, 최대 10개, AND)")
    @field:Size(max = 10)
    val member: List<String> = emptyList(),

    @field:Parameter(description = "동아리가 사용하는 교실")
    val room: ClubRoom? = null,

    @field:Parameter(description = "담당 선생님 이름(정확 일치)", example = "이진경")
    val teacher: String? = null,

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
