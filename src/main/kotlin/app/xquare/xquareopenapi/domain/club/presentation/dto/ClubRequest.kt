package app.xquare.xquareopenapi.domain.club.presentation.dto

import app.xquare.xquareopenapi.domain.club.ClubRoom
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 등록과 수정이 같이 쓴다. 수정도 모든 필드를 받아 통째로 바꾸므로, teacher를 null로 보내거나 빼면 담당 교직원이 없어진다.
// manager가 member에 있는지는 명세의 MANAGER_NOT_IN_MEMBER로 응답해야 해서 ClubRelationLoader가 검사한다.
data class ClubRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val name: String,

    // 파일 업로드 API가 없어서 키가 실제로 있는지는 보지 않는다.
    @field:NotBlank
    @field:Size(max = 255)
    val logo: String,

    val room: ClubRoom,

    val manager: Long,

    @field:Size(max = 50)
    // [1, null]처럼 null이 섞이면 Jackson이 읽지 못해 400 INVALID_INPUT이 된다.
    val member: List<Long>,

    val teacher: Long? = null,
)
