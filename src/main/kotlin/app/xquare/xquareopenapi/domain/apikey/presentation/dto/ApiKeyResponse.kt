package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.withSeoulOffset
import java.time.OffsetDateTime

// 원문은 DB에 없어서 이 응답에서만 볼 수 있다.
data class ApiKeyResponse(
    val apiKey: String,
    // 한국 시간(+09:00)
    val issuedAt: OffsetDateTime,
    // 발급 시각 + 60일. 한국 시간(+09:00)
    val expiresAt: OffsetDateTime,
) {
    companion object {
        fun of(apiKey: String, project: ApiProject) = ApiKeyResponse(
            apiKey = apiKey,
            issuedAt = project.issuedAt!!.withSeoulOffset(),
            expiresAt = project.expiresAt!!.withSeoulOffset(),
        )
    }
}
