package app.xquare.xquareopenapi.domain.apikey

// 분야의 접근 단계. 공개 단계(급식·시간표)는 키 없이 열려 분야가 없으므로 여기에 두지 않는다.
enum class AccessLevel {
    // 교내 정보, 개인정보 최소. 신청하면 바로 승인한다.
    SCHOOL,

    // 개인정보 포함. 관리자가 승인해야 쓸 수 있다.
    RESTRICTED,
}
