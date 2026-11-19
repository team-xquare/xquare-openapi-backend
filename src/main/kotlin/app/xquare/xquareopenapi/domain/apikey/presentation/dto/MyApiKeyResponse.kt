package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiKey
import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplication
import app.xquare.xquareopenapi.domain.apikey.withSeoulOffset
import java.time.OffsetDateTime

// 키 원문은 저장하지 않으므로 담지 않는다.
data class MyApiKeyResponse(
    // 지금 키 값을 발급한 시각. 재발급하면 바뀐다. 한국 시간(+09:00)
    val issuedAt: OffsetDateTime,
    // 만료 정책이 없어서 항상 null(만료되지 않음). 정책이 생기면 채운다.
    val expiresAt: OffsetDateTime?,
    // 신청 순서대로
    val fields: List<AppliedFieldResponse>,
) {
    companion object {
        fun of(apiKey: ApiKey, applications: List<ApiKeyApplication>) = MyApiKeyResponse(
            issuedAt = apiKey.issuedAt.withSeoulOffset(),
            expiresAt = null,
            fields = applications.map(AppliedFieldResponse::from),
        )
    }
}
