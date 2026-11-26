package app.xquare.xquareopenapi.domain.apikey.presentation.dto

// 서비스 URL 검증 규칙. http·https 주소만 받고 공백은 넣을 수 없다.
const val SERVICE_URL_PATTERN = "^https?://\\S+$"
const val SERVICE_URL_MAX_LENGTH = 255
