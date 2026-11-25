package app.xquare.xquareopenapi.domain.apikey

// 활용신청 분야. 키는 승인된 분야의 조회 API만 열 수 있다. 급식·시간표는 키 없이 여는 공개 API라 분야가 없다.
// 단계는 분야에 붙은 코드 상수다(관리자 화면에서 바꾸지 않음).
enum class ApiKeyField(val level: AccessLevel) {
    STUDENT(AccessLevel.RESTRICTED),
    TEACHER(AccessLevel.RESTRICTED),
    // 동아리 응답에 부원 학번·실명이 들어가므로 관리자 승인이 필요한 제한 단계다.
    CLUB(AccessLevel.RESTRICTED),
    SCHEDULE(AccessLevel.SCHOOL),
    STUDY_SUPERVISOR(AccessLevel.SCHOOL),
    ;

    // ApiKeyAuthenticationFilter가 키의 승인된 분야마다 이 권한을 주고, SecurityConfig가 경로별로 검사한다.
    val authority: String get() = "API_KEY_FIELD_$name"
}
