package app.xquare.xquareopenapi.domain.club.presentation.dto

import app.xquare.xquareopenapi.domain.club.ClubRoom
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 등록과 수정이 같이 쓴다. 수정도 모든 필드를 받아 통째로 바꾸므로, teacher를 null로 보내거나 빼면 담당 교직원이 없어진다.
// manager가 member에 있는지는 명세의 MANAGER_NOT_IN_MEMBER로 응답해야 해서 ClubRelationLoader가 검사한다.
@Schema(requiredProperties = ["name", "logo", "room", "manager", "member"])
data class ClubRequest(
    @field:NotBlank
    @field:Size(max = 20)
    @field:Schema(description = "동아리명(1~20자)", example = "DMS")
    val name: String,

    // 파일 업로드 API가 없어서 키가 실제로 있는지는 보지 않는다.
    @field:NotBlank
    @field:Size(max = 255)
    @field:Schema(description = "동아리 로고 이미지 키", example = "b0c39b24-792f-48cf-9492-ed238ed48486파일1.png")
    val logo: String,

    val room: ClubRoom,

    @field:Schema(description = "팀장의 학생 id(member에 포함)", example = "1")
    val manager: Long,

    @field:Size(max = 50)
    @field:Schema(description = "구성원 학생 id 리스트(최대 50개, 팀장 포함)", example = "[1, 2, 3, 4, 5]")
    // [1, null]처럼 null이 섞이면 Jackson이 읽지 못해 400 INVALID_INPUT이 된다.
    val member: List<Long>,

    @field:Schema(description = "담당 선생님의 교직원 id(생략·null 가능)", example = "3")
    val teacher: Long? = null,
)
