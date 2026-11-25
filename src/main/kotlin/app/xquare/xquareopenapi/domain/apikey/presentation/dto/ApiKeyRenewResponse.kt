package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiProject
import app.xquare.xquareopenapi.domain.apikey.withSeoulOffset
import java.time.OffsetDateTime

// 갱신은 키 값을 바꾸지 않으므로 원문 없이 시각만 준다. 한국 시간(+09:00)
data class ApiKeyRenewResponse(
    // 키 값을 발급한 시각. 갱신해도 바뀌지 않는다.
    val issuedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
) {
    companion object {
        fun from(project: ApiProject) = ApiKeyRenewResponse(
            issuedAt = project.issuedAt!!.withSeoulOffset(),
            expiresAt = project.expiresAt!!.withSeoulOffset(),
        )
    }
}
