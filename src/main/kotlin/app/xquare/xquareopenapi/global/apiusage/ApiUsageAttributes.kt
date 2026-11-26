package app.xquare.xquareopenapi.global.apiusage

// 인증 필터와 오류 응답 작성기가 요청 속성으로 남기고 ApiUsageLogCapture가 요청 완료 시 읽는 값의 키.
// 키 원문, 토큰, 이메일은 남기지 않는다.
object ApiUsageAttributes {
    // API 키가 가리키는 프로젝트 id(Long)
    const val PROJECT_ID = "xquare.apiUsage.projectId"

    // 호출한 사용자 id(Long). API 키 호출이면 프로젝트 주인(신청자), JWT 호출이면 로그인한 사용자다.
    const val USER_ID = "xquare.apiUsage.userId"

    // 키 원문의 앞 ApiUsageLog.KEY_PREFIX_MAX_LENGTH 글자(String). "xq_" 접두사 + 무작위 8글자라 원문을 복원할 수 없다.
    const val KEY_PREFIX = "xquare.apiUsage.keyPrefix"

    // 오류 응답 본문의 code(String)
    const val ERROR_CODE = "xquare.apiUsage.errorCode"
}
