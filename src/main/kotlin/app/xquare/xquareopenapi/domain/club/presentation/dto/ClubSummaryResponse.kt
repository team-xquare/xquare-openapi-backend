package app.xquare.xquareopenapi.domain.club.presentation.dto

import app.xquare.xquareopenapi.domain.club.Club
import io.swagger.v3.oas.annotations.media.Schema

// 학생의 소속 동아리, 교직원의 담당 동아리로 내려주는 동아리 정보(명세의 CLUB 타입).
@Schema(requiredProperties = ["id", "name", "logo"])
data class ClubSummaryResponse(
    val id: Long,
    val name: String,
    val logo: String,
) {
    companion object {
        fun from(club: Club?): ClubSummaryResponse? =
            if (club == null) null else ClubSummaryResponse(club.id!!, club.name, club.logo)
    }
}
