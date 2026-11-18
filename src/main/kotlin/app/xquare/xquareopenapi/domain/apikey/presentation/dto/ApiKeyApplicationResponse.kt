package app.xquare.xquareopenapi.domain.apikey.presentation.dto

import app.xquare.xquareopenapi.domain.apikey.ApiKeyApplicationType

// 첫 신청(FIRST)만 키 원문을 준다. 분야 추가(ADD_FIELD)는 기존 키를 그대로 쓰므로 apiKey가 null이다.
data class ApiKeyApplicationResponse(
    val type: ApiKeyApplicationType,
    val apiKey: String?,
)
