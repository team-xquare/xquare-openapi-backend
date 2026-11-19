package app.xquare.xquareopenapi.domain.apikey

// 활용신청 분야. 키는 신청한 분야의 조회 API만 열 수 있다. 급식은 키 없이 여는 API라 분야가 없다.
enum class ApiKeyField {
    STUDENT,
    TEACHER,
    CLUB,
    SCHEDULE,
    STUDY_SUPERVISOR,
    ;

    // ApiKeyAuthenticationFilter가 키의 분야마다 이 권한을 주고, SecurityConfig가 경로별로 검사한다.
    val authority: String get() = "API_KEY_FIELD_$name"
}
