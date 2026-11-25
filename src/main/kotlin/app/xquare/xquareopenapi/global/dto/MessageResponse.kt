package app.xquare.xquareopenapi.global.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(requiredProperties = ["message"])
data class MessageResponse(
    @field:Schema(description = "처리 결과 메시지", example = "생성되었습니다.")
    val message: String,
)
