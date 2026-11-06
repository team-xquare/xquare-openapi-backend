package app.xquare.xquareopenapi.domain.club.presentation.dto

import app.xquare.xquareopenapi.domain.club.ClubRoom
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class ClubSearchRequest(
    val name: String? = null,

    // member=성준&member=민수처럼 여러 번 줄 수 있다. 이름마다 조건이 하나씩 늘어나서 개수를 막는다.
    @field:Size(max = 10)
    val member: List<String> = emptyList(),

    val room: ClubRoom? = null,

    val teacher: String? = null,

    // page × size(최대 100)가 int를 넘으면 Spring Data가 offset을 만들지 못하고 500을 낸다.
    @field:Min(0)
    @field:Max(Int.MAX_VALUE / 100L)
    val page: Int = 0,

    @field:Min(1)
    @field:Max(100)
    val size: Int = 20,
)
