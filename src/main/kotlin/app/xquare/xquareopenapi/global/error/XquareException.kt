package app.xquare.xquareopenapi.global.error

import java.time.Duration

// retryAfter가 있으면 응답에 Retry-After 헤더(초)를 붙인다.
class XquareException(val errorCode: ErrorCode, val retryAfter: Duration? = null) : RuntimeException(errorCode.message)
