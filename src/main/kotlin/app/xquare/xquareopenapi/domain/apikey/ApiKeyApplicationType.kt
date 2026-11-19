package app.xquare.xquareopenapi.domain.apikey

enum class ApiKeyApplicationType {
    // 첫 신청. 이때만 키를 발급한다.
    FIRST,

    // 이미 키가 있는 사용자가 분야를 더하는 신청. 키는 그대로다.
    ADD_FIELD,
}
