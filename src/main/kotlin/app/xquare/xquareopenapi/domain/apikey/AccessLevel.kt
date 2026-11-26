package app.xquare.xquareopenapi.domain.apikey

// 분야의 접근 단계
enum class AccessLevel {
    // 누구나 쓰는 공개 정보(급식). 조회 API는 키 없이도 열려 있고, 분야를 신청하면 바로 승인한다.
    PUBLIC,

    // 교내 정보, 개인정보 최소. 신청하면 바로 승인한다.
    SCHOOL,

    // 개인정보 포함. 관리자가 승인해야 쓸 수 있다.
    RESTRICTED,
}
