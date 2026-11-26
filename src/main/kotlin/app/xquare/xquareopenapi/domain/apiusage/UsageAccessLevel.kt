package app.xquare.xquareopenapi.domain.apiusage

// 사용 기록에 남기는 호출의 접근 단계. 분야 단계(AccessLevel)에는 공개 단계가 없어서 기록 전용으로 따로 둔다.
// DB에는 이름 문자열로 저장하므로 AccessLevel에 값이 늘어도 스키마를 바꾸지 않는다.
enum class UsageAccessLevel {
    // 급식·시간표처럼 키 없이 여는 공개 조회
    PUBLIC,

    // 교내 정보 분야의 키 조회
    SCHOOL,

    // 개인정보가 포함된 분야의 키 조회
    RESTRICTED,
}
