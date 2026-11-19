package app.xquare.xquareopenapi.global.error

import java.time.Duration

// retryAfter가 있으면 응답에 Retry-After 헤더(초)를 붙인다.
// 기본 인자 대신 보조 생성자를 쓴다. 기본 인자는 모든 호출부에 비트마스크 상수를 만들어 등가 뮤턴트만 늘린다.
class XquareException(val errorCode: ErrorCode, val retryAfter: Duration?) : RuntimeException(errorCode.message) {
    constructor(errorCode: ErrorCode) : this(errorCode, null)
}
