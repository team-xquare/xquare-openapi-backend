package app.xquare.xquareopenapi.domain.auth.presentation.dto

import jakarta.validation.constraints.NotBlank

data class ReissueRequest(
    @field:NotBlank
    val refreshToken: String,
)
