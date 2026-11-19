package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ApiKeyRequest(
    @field:NotBlank
    @field:Size(max = 30)
    val serviceName: String,

    @field:NotBlank
    @field:Size(max = 200)
    val purpose: String,
)
