package app.xquare.xquareopenapi.domain.apikey

// 분야 신청의 구분. 프로젝트에 처음 들어온 신청 요청의 분야는 FIRST, 그 뒤 요청의 분야는 ADDITIONAL이다.
// 신청할 때 정해져 바뀌지 않는다(반려 후 재신청도 처음 구분을 유지).
enum class ApplicationType {
    FIRST,
    ADDITIONAL,
}
